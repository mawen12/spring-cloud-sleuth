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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;

/**
 * 用于支持跟踪{@link ExecutorService}的装饰类
 *
 * @author Gaurav Rai Mazra
 * @see TraceCallable
 * @see TraceRunnable
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class TraceableExecutorService implements ExecutorService {

	static final Map<ExecutorService/* 原始的ExecutorService */, TraceableExecutorService/* 对原始ExecutorService进行封装，支持追踪 */> CACHE = new ConcurrentHashMap<>();

	/**
	 * 原始的ExecutorService
	 */
	final ExecutorService delegate;

	/**
	 * Span名称
	 */
	final String spanName;

	/**
	 * 跟踪器
	 */
	Tracer tracer;

	/**
	 * Span名称生成器
	 */
	SpanNamer spanNamer;

	/**
	 * Bean工厂
	 */
	BeanFactory beanFactory;

	public TraceableExecutorService(BeanFactory beanFactory, final ExecutorService delegate) {
		this(beanFactory, delegate, null);
	}

	public TraceableExecutorService(BeanFactory beanFactory, final ExecutorService delegate, String spanName) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
		this.spanName = spanName;
	}

	/**
	 * 将原始的ExecutorService保存到缓存中，并生成其对应的包装类{@link TraceableExecutorService}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 * @param beanName    bean name
	 *
	 * @return traced instance
	 */
	public static TraceableExecutorService wrap(BeanFactory beanFactory, ExecutorService delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new TraceableExecutorService(beanFactory, delegate, beanName));
	}

	/**
	 * 将原始的ExecutorService保存到缓存中，并生成其对应的包装类{@link TraceableExecutorService}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 *
	 * @return traced instance
	 */
	public static TraceableExecutorService wrap(BeanFactory beanFactory, ExecutorService delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new TraceableExecutorService(beanFactory, delegate, null));
	}

	@Override
	public void execute(Runnable command) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
		// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
		this.delegate.execute(ContextUtil.isContextUnusable(this.beanFactory) ? command : new TraceRunnable(tracer(), spanNamer(), command, this.spanName));
	}

	@Override
	public void shutdown() {
		try {
			// 停止原始ExecutorService
			this.delegate.shutdown();
		} finally {
			// 将已停止的ExecutorService移除
			CACHE.remove(this.delegate);
		}
	}

	@Override
	public List<Runnable> shutdownNow() {
		try {
			// 立即停止ExecutorService
			return this.delegate.shutdownNow();
		} finally {
			// 将已停止的ExecutorService移除
			CACHE.remove(this.delegate);
		}
	}

	@Override
	public boolean isShutdown() {
		return this.delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return this.delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return this.delegate.awaitTermination(timeout, unit);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return this.delegate.submit(ContextUtil.isContextUnusable(this.beanFactory) ? task : new TraceCallable<>(tracer(), spanNamer(), task, this.spanName));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
		// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
		return this.delegate.submit(ContextUtil.isContextUnusable(this.beanFactory) ? task : new TraceRunnable(tracer(), spanNamer(), task, this.spanName), result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
		// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
		return this.delegate.submit(ContextUtil.isContextUnusable(this.beanFactory) ? task : new TraceRunnable(tracer(), spanNamer(), task, this.spanName));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable列表来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return this.delegate.invokeAll(ContextUtil.isContextUnusable(this.beanFactory) ? tasks : wrapCallableCollection(tasks));
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable列表来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return this.delegate.invokeAll(ContextUtil.isContextUnusable(this.beanFactory) ? tasks : wrapCallableCollection(tasks), timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable列表来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return this.delegate.invokeAny(ContextUtil.isContextUnusable(this.beanFactory) ? tasks : wrapCallableCollection(tasks));
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable列表来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return this.delegate.invokeAny(ContextUtil.isContextUnusable(this.beanFactory) ? tasks : wrapCallableCollection(tasks), timeout, unit);
	}

	/**
	 * 将原始的{@link Callable}包装为{@link TraceCallable}，
	 * 对于原本就是{@link TraceCallable}的便不会处理，也不会反映在结果中
	 *
	 * @param tasks
	 * @param <T>
	 *
	 * @return
	 */
	private <T> Collection<? extends Callable<T>> wrapCallableCollection(Collection<? extends Callable<T>> tasks) {
		List<Callable<T>> ts = new ArrayList<>();
		for (Callable<T> task : tasks) {
			// TODO by mawen if task instanceof TraceCallable, it should be directly add to ts
			if (!(task instanceof TraceCallable)) {
				ts.add(new TraceCallable<>(tracer(), spanNamer(), task, this.spanName));
			}
		}
		return ts;
	}

	/**
	 * @return 返回跟踪器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	Tracer tracer() {
		if (this.tracer == null && this.beanFactory != null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

	/**
	 * 需要注意的是，如果不存在SpanNamer这个Bean，那么将返回空
	 *
	 * @return 返回Span名称生成器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 *
	 * @see LazyTraceThreadPoolTaskExecutor#spanNamer()
	 */
	SpanNamer spanNamer() {
		if (this.spanNamer == null && this.beanFactory != null) {
			this.spanNamer = this.beanFactory.getBean(SpanNamer.class);
		}
		return this.spanNamer;
	}

}
