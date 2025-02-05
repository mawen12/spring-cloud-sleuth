/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.CustomizableThreadCreator;
import org.springframework.util.ErrorHandler;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * 用于支持跟踪的{@link ThreadPoolTaskScheduler}的实现。当其他方法都失败时，应仅将其作为最后的手段使用。
 *
 * @author Marcin Grzejszczak
 * @since 2.0.4
 */
// TODO: Think of a better solution than this
class LazyTraceThreadPoolTaskScheduler extends ThreadPoolTaskScheduler {

	private static final Log log = LogFactory.getLog(LazyTraceThreadPoolTaskScheduler.class);

	private static final Map<ThreadPoolTaskScheduler/* 原始的ThreadPoolTaskScheduler */, LazyTraceThreadPoolTaskScheduler/* 被代理的对象 */> CACHE = new ConcurrentHashMap<>();

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * 被包装的原始类
	 */
	private final ThreadPoolTaskScheduler delegate;

	/**
	 * Bean名称
	 */
	private final String beanName;

	/**
	 * {@link ThreadPoolTaskScheduler#initializeExecutor(ThreadFactory, RejectedExecutionHandler)}方法引用
	 */
	private final Method initializeExecutor;

	/**
	 * {@link ThreadPoolTaskScheduler#createExecutor(int, ThreadFactory, RejectedExecutionHandler)}方法引用
	 */
	private final Method createExecutor;

	/**
	 * {@link ThreadPoolTaskScheduler#cancelRemainingTask(Runnable)}方法引用
	 */
	private final Method cancelRemainingTask;

	/**
	 * {@link ThreadPoolTaskScheduler#nextThreadName()}方法引用
	 */
	private final Method nextThreadName;

	/**
	 * {@link CustomizableThreadCreator#getDefaultThreadNamePrefix()}方法引用
	 */
	private final Method getDefaultThreadNamePrefix;

	/**
	 * 跟踪器
	 */
	private Tracer tracing;

	/**
	 * Span命名器
	 */
	private SpanNamer spanNamer;

	LazyTraceThreadPoolTaskScheduler(BeanFactory beanFactory, ThreadPoolTaskScheduler delegate, String beanName) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
		// 获取initializeExecutor方法
		this.initializeExecutor = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class, "initializeExecutor", null);
		makeAccessibleIfNotNull(this.initializeExecutor);
		// 获取createExecutor方法
		this.createExecutor = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class, "createExecutor", null);
		makeAccessibleIfNotNull(this.createExecutor);
		// 获取cancelRemainingTask方法
		this.cancelRemainingTask = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class, "cancelRemainingTask", null);
		makeAccessibleIfNotNull(this.cancelRemainingTask);
		// 获取nextThreadName方法
		this.nextThreadName = ReflectionUtils.findMethod(ThreadPoolTaskScheduler.class, "nextThreadName", null);
		makeAccessibleIfNotNull(this.nextThreadName);
		// 获取getDefaultThreadNamePrefix方法
		this.getDefaultThreadNamePrefix = ReflectionUtils.findMethod(CustomizableThreadCreator.class, "getDefaultThreadNamePrefix", null);
		makeAccessibleIfNotNull(this.getDefaultThreadNamePrefix);
	}

	/**
	 * 将原始的ThreadPoolTaskScheduler保存到缓存中，并生成其对应的包装类{@link LazyTraceThreadPoolTaskScheduler}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate delegate to wrap
	 * @param beanName bean name
	 * @return traced instance
	 */
	static LazyTraceThreadPoolTaskScheduler wrap(BeanFactory beanFactory, @NonNull ThreadPoolTaskScheduler delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceThreadPoolTaskScheduler(beanFactory, delegate, beanName));
	}

	private void makeAccessibleIfNotNull(Method method) {
		if (method != null) {
			ReflectionUtils.makeAccessible(method);
		}
	}

	/**
	 * 将{@link Runnable}转换为{@link TraceRunnable}
	 *
	 * <p>如果Spring应用上下文未启动，则无法转换
	 *
	 * <p>直接执行不会产生Span，通过TraceRunnable来执行会生成Span
	 *
	 * @param delegate
	 * @return
	 */
	private Runnable traceRunnableWhenContextReady(Runnable delegate) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return delegate;
		}
		if (delegate instanceof TraceRunnable) {
			return delegate;
		}
		return new TraceRunnable(tracing(), spanNamer(), delegate, this.beanName);
	}

	/**
	 * 将{@link Callable}转换为{@link TraceCallable}
	 *
	 * <p>如果Spring应用上下文未启动，则无法转换
	 *
	 * <p>直接执行不会产生Span，通过TraceCallable来执行会生成Span
	 *
	 * @param delegate
	 * @return
	 * @param <V>
	 */
	private <V> Callable<V> traceCallableWhenContextReady(Callable<V> delegate) {
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			return delegate;
		}
		if (delegate instanceof TraceCallable) {
			return delegate;
		}
		return new TraceCallable<>(tracing(), spanNamer(), delegate, this.beanName);
	}

	@Override
	public void setPoolSize(int poolSize) {
		this.delegate.setPoolSize(poolSize);
	}

	@Override
	public void setRemoveOnCancelPolicy(boolean removeOnCancelPolicy) {
		this.delegate.setRemoveOnCancelPolicy(removeOnCancelPolicy);
	}

	@Override
	public void setErrorHandler(ErrorHandler errorHandler) {
		this.delegate.setErrorHandler(errorHandler);
	}

	@Override
	public ExecutorService initializeExecutor(ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
		// 调用initializeExecutor生成ExecutorService
		ExecutorService executorService = (ExecutorService) ReflectionUtils.invokeMethod(this.initializeExecutor, this.delegate, traceThreadFactory(threadFactory), traceRejectedExecutionHandler(rejectedExecutionHandler));
		if (executorService instanceof TraceableScheduledExecutorService) {
			return executorService;
		}
		// 将原始的ExecutorService包装为TraceableExecutorService
		return TraceableExecutorService.wrap(this.beanFactory, executorService, this.beanName);
	}

	private RejectedExecutionHandler traceRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
		return new RejectedExecutionHandler() {
			@Override
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				// 将Runnable包装为TraceRunnable
				rejectedExecutionHandler.rejectedExecution(traceRunnableWhenContextReady(r), executor);
			}
		};
	}

	private ThreadFactory traceThreadFactory(ThreadFactory threadFactory) {
		// 将Runnable包装为TraceRunnable
		return r -> threadFactory.newThread(traceRunnableWhenContextReady(r));
	}

	@Override
	public ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
		// 调用createExecutor生成ScheduledExecutorService
		ScheduledExecutorService executorService = (ScheduledExecutorService) ReflectionUtils.invokeMethod(this.createExecutor, this.delegate, poolSize, traceThreadFactory(threadFactory), traceRejectedExecutionHandler(rejectedExecutionHandler));
		if (executorService instanceof TraceableScheduledExecutorService) {
			return executorService;
		}
		// 将原始的ScheduledExecutorService包装为TraceableScheduledExecutorService
		return TraceableScheduledExecutorService.wrap(this.beanFactory, executorService, this.beanName);
	}

	/**
	 * @return 返回包装过后的TraceableScheduledExecutorService
	 * @throws IllegalStateException
	 */
	@Override
	public ScheduledExecutorService getScheduledExecutor() throws IllegalStateException {
		ScheduledExecutorService executor = this.delegate.getScheduledExecutor();
		return executor instanceof TraceableScheduledExecutorService ? executor : TraceableScheduledExecutorService.wrap(this.beanFactory, executor, this.beanName);
	}

	/**
	 * @return 返回包装过后的LazyTraceScheduledThreadPoolExecutor
	 * @throws IllegalStateException
	 */
	@Override
	public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() throws IllegalStateException {
		// 获取原始的ScheduledThreadPoolExecutor
		ScheduledThreadPoolExecutor executor = this.delegate.getScheduledThreadPoolExecutor();
		if (executor instanceof LazyTraceScheduledThreadPoolExecutor) {
			return executor;
		}
		// 将原始的ScheduledThreadPoolExecutor包装为LazyTraceScheduledThreadPoolExecutor
		return LazyTraceScheduledThreadPoolExecutor.wrap(executor.getCorePoolSize(), executor.getThreadFactory(), executor.getRejectedExecutionHandler(), this.beanFactory, executor, this.beanName);
	}

	@Override
	public int getPoolSize() {
		return this.delegate.getPoolSize();
	}

	@Override
	public boolean isRemoveOnCancelPolicy() {
		return this.delegate.isRemoveOnCancelPolicy();
	}

	@Override
	public int getActiveCount() {
		return this.delegate.getActiveCount();
	}

	@Override
	public void execute(Runnable task) {
		// 将Runnable包装为TraceRunnable
		this.delegate.execute(traceRunnableWhenContextReady(task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		// 将Runnable包装为TraceRunnable
		this.delegate.execute(traceRunnableWhenContextReady(task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.submit(traceRunnableWhenContextReady(task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		// 将Callable包装为TraceCallable
		return this.delegate.submit(traceCallableWhenContextReady(task));
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.submitListenable(traceRunnableWhenContextReady(task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		// 将Callable包装为TraceCallable
		return this.delegate.submitListenable(traceCallableWhenContextReady(task));
	}

	@Override
	public void cancelRemainingTask(Runnable task) {
		// 将Runnable包装为TraceRunnable
		ReflectionUtils.invokeMethod(this.cancelRemainingTask, this.delegate, traceRunnableWhenContextReady(task));
	}

	@Override
	public boolean prefersShortLivedTasks() {
		return this.delegate.prefersShortLivedTasks();
	}

	@Override
	@Nullable
	public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.schedule(traceRunnableWhenContextReady(task), trigger);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.schedule(traceRunnableWhenContextReady(task), startTime);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task), startTime, period);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task), period);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task), startTime, delay);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task), delay);
	}

	@Override
	public void setThreadFactory(ThreadFactory threadFactory) {
		this.delegate.setThreadFactory(threadFactory);
	}

	@Override
	public void setThreadNamePrefix(String threadNamePrefix) {
		this.delegate.setThreadNamePrefix(threadNamePrefix);
	}

	@Override
	public void setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
		this.delegate.setRejectedExecutionHandler(rejectedExecutionHandler);
	}

	@Override
	public void setWaitForTasksToCompleteOnShutdown(boolean waitForJobsToCompleteOnShutdown) {
		this.delegate.setWaitForTasksToCompleteOnShutdown(waitForJobsToCompleteOnShutdown);
	}

	@Override
	public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
		this.delegate.setAwaitTerminationSeconds(awaitTerminationSeconds);
	}

	@Override
	public void setBeanName(String name) {
		this.delegate.setBeanName(name);
	}

	@Override
	public void afterPropertiesSet() {
		this.delegate.afterPropertiesSet();
	}

	@Override
	public void initialize() {
		this.delegate.initialize();
	}

	@Override
	public void destroy() {
		this.delegate.destroy();
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
	}

	@Override
	public Thread newThread(Runnable runnable) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.newThread(traceRunnableWhenContextReady(runnable));
	}

	@Override
	public String getThreadNamePrefix() {
		return this.delegate.getThreadNamePrefix();
	}

	@Override
	public void setThreadPriority(int threadPriority) {
		this.delegate.setThreadPriority(threadPriority);
	}

	@Override
	public int getThreadPriority() {
		return this.delegate.getThreadPriority();
	}

	@Override
	public void setDaemon(boolean daemon) {
		this.delegate.setDaemon(daemon);
	}

	@Override
	public boolean isDaemon() {
		return this.delegate.isDaemon();
	}

	@Override
	public void setThreadGroupName(String name) {
		this.delegate.setThreadGroupName(name);
	}

	@Override
	public void setThreadGroup(ThreadGroup threadGroup) {
		this.delegate.setThreadGroup(threadGroup);
	}

	@Override
	@Nullable
	public ThreadGroup getThreadGroup() {
		return this.delegate.getThreadGroup();
	}

	@Override
	public Thread createThread(Runnable runnable) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.createThread(traceRunnableWhenContextReady(runnable));
	}

	@Override
	public String nextThreadName() {
		return (String) ReflectionUtils.invokeMethod(this.nextThreadName, this.delegate);
	}

	@Override
	public String getDefaultThreadNamePrefix() {
		if (this.delegate == null) {
			return super.getDefaultThreadNamePrefix();
		}
		return (String) ReflectionUtils.invokeMethod(this.getDefaultThreadNamePrefix, this.delegate);
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.schedule(traceRunnableWhenContextReady(task), startTime);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task), startTime, period);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleAtFixedRate(traceRunnableWhenContextReady(task), period);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task), startTime, delay);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
		// 将Runnable包装为TraceRunnable
		return this.delegate.scheduleWithFixedDelay(traceRunnableWhenContextReady(task), delay);
	}

	/**
	 * @return 返回跟踪器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	private Tracer tracing() {
		if (this.tracing == null) {
			this.tracing = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracing;
	}

	/**
	 * 需要注意的时候，即使不存在SpanNamer这个Bean，会返回{@link DefaultSpanNamer}作为兜底
	 *
	 * @return 返回Span名称生成器，如果不存在则从{@link BeanFactory#getBean(Class)}获取，如果BeanFactory中不存在，则返回{@link DefaultSpanNamer}
	 */
	private SpanNamer spanNamer() {
		if (this.spanNamer == null) {
			try {
				this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				log.warn("SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}

}
