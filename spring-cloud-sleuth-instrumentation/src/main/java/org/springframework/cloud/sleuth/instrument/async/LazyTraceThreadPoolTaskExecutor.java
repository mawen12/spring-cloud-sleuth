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

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
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
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

/**
 * 用于支持跟踪的{@link ThreadPoolTaskExecutor}的实现
 *
 * <p>由于线程池执行任务是从任务队列取出的，可能存在延迟的情况
 *
 * @author Marcin Grzejszczak
 * @since 1.0.10
 */
@SuppressWarnings("serial")
public class LazyTraceThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {

	private static final Log log = LogFactory.getLog(LazyTraceThreadPoolTaskExecutor.class);

	private static final Map<ThreadPoolTaskExecutor/* 原始实例 */, LazyTraceThreadPoolTaskExecutor/* 被代理的对象 */> CACHE = new ConcurrentHashMap<>();

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * 被包装的原始类
	 */
	private final ThreadPoolTaskExecutor delegate;

	/**
	 * Bean名称
	 */
	private final String beanName;

	/**
	 * 跟踪器
	 */
	private Tracer tracer;

	/**
	 * Span命名器
	 */
	private SpanNamer spanNamer;

	public LazyTraceThreadPoolTaskExecutor(BeanFactory beanFactory, ThreadPoolTaskExecutor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = null;
	}

	public LazyTraceThreadPoolTaskExecutor(BeanFactory beanFactory, ThreadPoolTaskExecutor delegate, String beanName) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
	}

	/**
	 * 将原始的ThreadPoolTaskExecutor保存到缓存中，并生成其对应的包装类{@link LazyTraceThreadPoolTaskExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 * @param beanName    bean name
	 *
	 * @return traced instance
	 */
	public static LazyTraceThreadPoolTaskExecutor wrap(BeanFactory beanFactory, @NonNull ThreadPoolTaskExecutor delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceThreadPoolTaskExecutor(beanFactory, delegate, beanName));
	}

	/**
	 * 将原始的ThreadPoolTaskExecutor保存到缓存中，并生成其对应的包装类{@link LazyTraceThreadPoolTaskExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 *
	 * @return traced instance
	 */
	public static LazyTraceThreadPoolTaskExecutor wrap(BeanFactory beanFactory, @NonNull ThreadPoolTaskExecutor delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceThreadPoolTaskExecutor(beanFactory, delegate, null));
	}

	@Override
	public void execute(Runnable task) {
		// 对Runnable进行包装，确保执行时生成Span
		this.delegate.execute(wrap(task));
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		// 对Runnable进行包装，确保执行时生成Span
		this.delegate.execute(wrap(task), startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		// 对Runnable进行包装，确保执行时生成Span
		return this.delegate.submit(wrap(task));
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		// 对Callable进行包装，确保执行时生成Span
		return this.delegate.submit(wrap(task));
	}

	@Override
	public ListenableFuture<?> submitListenable(Runnable task) {
		// 对Runnable进行包装，确保执行时生成Span
		return this.delegate.submitListenable(wrap(task));
	}

	@Override
	public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
		// 对Callable进行包装，确保执行时生成Span
		return this.delegate.submitListenable(wrap(task));
	}

	@Override
	public boolean prefersShortLivedTasks() {
		return this.delegate.prefersShortLivedTasks();
	}

	@Override
	public void setThreadFactory(ThreadFactory threadFactory) {
		this.delegate.setThreadFactory(threadFactory);
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
	public ThreadPoolExecutor getThreadPoolExecutor() throws IllegalStateException {
		return this.delegate.getThreadPoolExecutor();
	}

	@Override
	public int getPoolSize() {
		return this.delegate.getPoolSize();
	}

	@Override
	public int getActiveCount() {
		return this.delegate.getActiveCount();
	}

	@Override
	public int getQueueCapacity() {
		return this.delegate.getQueueCapacity();
	}

	@Override
	public int getQueueSize() {
		return this.delegate.getQueueSize();
	}

	@Override
	public void destroy() {
		this.delegate.destroy();
		super.destroy();
	}

	@Override
	public void afterPropertiesSet() {
		this.delegate.afterPropertiesSet();
		super.afterPropertiesSet();
	}

	@Override
	public void initialize() {
		this.delegate.initialize();
	}

	@Override
	public void shutdown() {
		this.delegate.shutdown();
		super.shutdown();
	}

	@Override
	public Thread newThread(Runnable runnable) {
		// 对Runnable进行包装，确保执行时生成Span
		return this.delegate.newThread(wrap(runnable));
	}

	private Runnable wrap(Runnable runnable) {
		// 如果是TraceRunnable，则无需包装
		if (runnable instanceof TraceRunnable) {
			return runnable;
		}
		// 如果Spring应用上下文尚未启动，则不进行包装，否则使用TraceRunnable进行包装
		// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
		return ContextUtil.isContextUnusable(this.beanFactory) ? runnable : new TraceRunnable(tracer(), spanNamer(), runnable, this.beanName);
	}

	private <V> Callable<V> wrap(Callable<V> callable) {
		// 如果是TraceCallable，则无需包装
		if (callable instanceof TraceCallable) {
			return callable;
		}
		// 如果Spring应用上下文尚未启动，则不进行包装，否则使用TraceCallable进行包装
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return ContextUtil.isContextUnusable(this.beanFactory) ? callable : new TraceCallable<>(tracer(), spanNamer(), callable, this.beanName);
	}

	@Override
	public String getThreadNamePrefix() {
		return this.delegate.getThreadNamePrefix();
	}

	@Override
	public void setThreadNamePrefix(String threadNamePrefix) {
		this.delegate.setThreadNamePrefix(threadNamePrefix);
	}

	@Override
	public int getThreadPriority() {
		return this.delegate.getThreadPriority();
	}

	@Override
	public void setThreadPriority(int threadPriority) {
		this.delegate.setThreadPriority(threadPriority);
	}

	@Override
	public boolean isDaemon() {
		return this.delegate.isDaemon();
	}

	@Override
	public void setDaemon(boolean daemon) {
		this.delegate.setDaemon(daemon);
	}

	@Override
	public void setThreadGroupName(String name) {
		this.delegate.setThreadGroupName(name);
	}

	@Override
	public ThreadGroup getThreadGroup() {
		return this.delegate.getThreadGroup();
	}

	@Override
	public void setThreadGroup(ThreadGroup threadGroup) {
		this.delegate.setThreadGroup(threadGroup);
	}

	@Override
	public Thread createThread(Runnable runnable) {
		return this.delegate.createThread(wrap(runnable));
	}

	@Override
	public int getCorePoolSize() {
		return this.delegate.getCorePoolSize();
	}

	@Override
	public void setCorePoolSize(int corePoolSize) {
		this.delegate.setCorePoolSize(corePoolSize);
	}

	@Override
	public int getMaxPoolSize() {
		return this.delegate.getMaxPoolSize();
	}

	@Override
	public void setMaxPoolSize(int maxPoolSize) {
		this.delegate.setMaxPoolSize(maxPoolSize);
	}

	@Override
	public int getKeepAliveSeconds() {
		return this.delegate.getKeepAliveSeconds();
	}

	@Override
	public void setKeepAliveSeconds(int keepAliveSeconds) {
		this.delegate.setKeepAliveSeconds(keepAliveSeconds);
	}

	@Override
	public void setQueueCapacity(int queueCapacity) {
		this.delegate.setQueueCapacity(queueCapacity);
	}

	@Override
	public void setAllowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
		this.delegate.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
	}

	@Override
	public void setTaskDecorator(TaskDecorator taskDecorator) {
		this.delegate.setTaskDecorator(taskDecorator);
	}

	/**
	 * @return 返回跟踪器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	/**
	 * 需要注意的时候，即使不存在SpanNamer这个Bean，会返回{@link DefaultSpanNamer}作为兜底
	 *
	 * @return 返回Span名称生成器，如果不存在则从{@link BeanFactory#getBean(Class)}获取，如果BeanFactory中不存在，则返回{@link DefaultSpanNamer}
	 *
	 * @see TraceableExecutorService#spanNamer()
	 */
	private SpanNamer spanNamer() {
		if (this.spanNamer == null) {
			try {
				this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
			} catch (NoSuchBeanDefinitionException e) {
				log.warn("SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}

}
