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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;

/**
 * 用于支持跟踪{@link ScheduledExecutorService}的装饰类
 *
 * @author Gaurav Rai Mazra
 * @see TraceRunnable
 * @see TraceCallable
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class TraceableScheduledExecutorService extends TraceableExecutorService implements ScheduledExecutorService {

	private static final Map<ExecutorService/* 原始的ScheduledExecutorService */, TraceableScheduledExecutorService/* 对原始ScheduledExecutorService进行封装，支持跟踪 */> CACHE = new ConcurrentHashMap<>();

	public TraceableScheduledExecutorService(BeanFactory beanFactory, final ExecutorService delegate) {
		super(beanFactory, delegate);
	}

	public TraceableScheduledExecutorService(BeanFactory beanFactory, final ExecutorService delegate, String beanName) {
		super(beanFactory, delegate, beanName);
	}

	/**
	 * 将原始的ExecutorService保存到缓存中，并生成其对应的包装类{@link TraceableScheduledExecutorService}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 * @param beanName    bean name
	 *
	 * @return traced instance
	 */
	public static TraceableScheduledExecutorService wrap(BeanFactory beanFactory, ExecutorService delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new TraceableScheduledExecutorService(beanFactory, delegate, beanName));
	}

	/**
	 * 将原始的ExecutorService保存到缓存中，并生成其对应的包装类{@link TraceableScheduledExecutorService}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 *
	 * @return traced instance
	 */
	public static TraceableScheduledExecutorService wrap(BeanFactory beanFactory, ExecutorService delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new TraceableScheduledExecutorService(beanFactory, delegate, null));
	}

	private ScheduledExecutorService getScheduledExecutorService() {
		return (ScheduledExecutorService) this.delegate;
	}

	@Override
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
		// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
		return getScheduledExecutorService().schedule(ContextUtil.isContextUnusable(this.beanFactory) ? command : new TraceRunnable(tracer(), spanNamer(), command, this.spanName), delay, unit);
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceCallable来执行。
		// 直接执行不会产生Span，通过TraceCallable来执行会生成Span
		return getScheduledExecutorService().schedule(ContextUtil.isContextUnusable(this.beanFactory) ? callable : new TraceCallable<>(tracer(), spanNamer(), callable, this.spanName), delay, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
		return getScheduledExecutorService()
				.scheduleAtFixedRate(
						// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
						// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
						ContextUtil.isContextUnusable(this.beanFactory) ? command : new TraceRunnable(tracer(), spanNamer(), command, this.spanName), initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return getScheduledExecutorService()
				.scheduleWithFixedDelay(
						// 如果Spring应用上下文尚未准备好，则直接执行，否则包装为TraceRunnable来执行。
						// 直接执行不会产生Span，通过TraceRunnable来执行会生成Span
						ContextUtil.isContextUnusable(this.beanFactory) ? command : new TraceRunnable(tracer(), spanNamer(), command, this.spanName), initialDelay, delay, unit);
	}

}
