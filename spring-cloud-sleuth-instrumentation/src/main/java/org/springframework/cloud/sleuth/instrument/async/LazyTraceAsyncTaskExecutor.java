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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.lang.NonNull;

/**
 * 用于支持跟踪的{@link AsyncTaskExecutor}实现。
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
// public as most types in this package were documented for use
public class LazyTraceAsyncTaskExecutor implements AsyncTaskExecutor {

	private static final Map<AsyncTaskExecutor/* 原始的Executor */, LazyTraceAsyncTaskExecutor/* 被代理的对象 */> CACHE = new ConcurrentHashMap<>();

	private static final Log log = LogFactory.getLog(LazyTraceAsyncTaskExecutor.class);

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * 被包装的原始类
	 */
	private final AsyncTaskExecutor delegate;

	/**
	 * Bean名称
	 */
	private final String beanName;

	/**
	 * 跟踪器
	 */
	private Tracer tracing;

	/**
	 * Span命名器
	 */
	private SpanNamer spanNamer;

	public LazyTraceAsyncTaskExecutor(BeanFactory beanFactory, AsyncTaskExecutor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = null;
	}

	public LazyTraceAsyncTaskExecutor(BeanFactory beanFactory, AsyncTaskExecutor delegate, String beanName) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
	}

	/**
	 * 将原始的AsyncTaskExecutor保存到缓存中，并生成其对应的包装类{@link AsyncTaskExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate delegate to wrap
	 * @param beanName bean name
	 * @return traced instance
	 */
	public static LazyTraceAsyncTaskExecutor wrap(BeanFactory beanFactory, @NonNull AsyncTaskExecutor delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceAsyncTaskExecutor(beanFactory, delegate, beanName));
	}

	/**
	 * 将原始的AsyncTaskExecutor保存到缓存中，并生成其对应的包装类{@link AsyncTaskExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate delegate to wrap
	 * @return traced instance
	 */
	public static LazyTraceAsyncTaskExecutor wrap(BeanFactory beanFactory, @NonNull AsyncTaskExecutor delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceAsyncTaskExecutor(beanFactory, delegate, null));
	}

	@Override
	public void execute(Runnable task) {
		Runnable taskToRun = task;
		// 将Runnable包装为TraceRunnable
		if (!ContextUtil.isContextUnusable(this.beanFactory)) {
			taskToRun = new TraceRunnable(tracing(), spanNamer(), task, this.beanName);
		}
		this.delegate.execute(taskToRun);
	}

	@Override
	public void execute(Runnable task, long startTimeout) {
		Runnable taskToRun = task;
		// 将Runnable包装为TraceRunnable
		if (!ContextUtil.isContextUnusable(this.beanFactory)) {
			taskToRun = new TraceRunnable(tracing(), spanNamer(), task, this.beanName);
		}
		this.delegate.execute(taskToRun, startTimeout);
	}

	@Override
	public Future<?> submit(Runnable task) {
		Runnable taskToRun = task;
		// 将Runnable包装为TraceRunnable
		if (!ContextUtil.isContextUnusable(this.beanFactory)) {
			taskToRun = new TraceRunnable(tracing(), spanNamer(), task, this.beanName);
		}
		return this.delegate.submit(taskToRun);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		Callable<T> taskToRun = task;
		// 将Callable包装为TraceCallable
		if (!ContextUtil.isContextUnusable(this.beanFactory)) {
			taskToRun = new TraceCallable<>(tracing(), spanNamer(), task, this.beanName);
		}
		return this.delegate.submit(taskToRun);
	}

	// due to some race conditions trace keys might not be ready yet
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

	/**
	 * @return 返回跟踪器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	private Tracer tracing() {
		if (this.tracing == null) {
			try {
				this.tracing = this.beanFactory.getBean(Tracer.class);
			}
			catch (NoSuchBeanDefinitionException e) {
				return null;
			}
		}
		return this.tracing;
	}

}
