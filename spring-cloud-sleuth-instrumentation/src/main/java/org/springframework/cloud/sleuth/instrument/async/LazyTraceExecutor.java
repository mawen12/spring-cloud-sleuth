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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.internal.ContextUtil;
import org.springframework.cloud.sleuth.internal.DefaultSpanNamer;
import org.springframework.lang.NonNull;

/**
 * 用于支持跟踪的{@link Executor}实现。
 *
 * @author Dave Syer
 * @see TraceRunnable
 * @see TraceCallable
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class LazyTraceExecutor implements Executor {

	private static final Log log = LogFactory.getLog(LazyTraceExecutor.class);

	private static final Map<Executor/* 原始的Executor */, LazyTraceExecutor/* 被代理的对象 */> CACHE = new ConcurrentHashMap<>();

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * 被包装的原始类
	 */
	private final Executor delegate;

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

	public LazyTraceExecutor(BeanFactory beanFactory, Executor delegate) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = null;
	}

	public LazyTraceExecutor(BeanFactory beanFactory, Executor delegate, String beanName) {
		this.beanFactory = beanFactory;
		this.delegate = delegate;
		this.beanName = beanName;
	}

	/**
	 * 将原始的Executor保存到缓存中，并生成其对应的包装类{@link LazyTraceExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 * @param beanName    bean name
	 *
	 * @return traced instance
	 */
	public static LazyTraceExecutor wrap(BeanFactory beanFactory, @NonNull Executor delegate, String beanName) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceExecutor(beanFactory, delegate, beanName));
	}

	/**
	 * 将原始的Executor保存到缓存中，并生成其对应的包装类{@link LazyTraceExecutor}
	 *
	 * @param beanFactory 能够提供{@link Tracer}和{@link SpanNamer}的Bean工厂
	 * @param delegate    delegate to wrap
	 *
	 * @return traced instance
	 */
	public static LazyTraceExecutor wrap(BeanFactory beanFactory, @NonNull Executor delegate) {
		return CACHE.computeIfAbsent(delegate, e -> new LazyTraceExecutor(beanFactory, delegate, null));
	}

	@Override
	public void execute(Runnable command) {
		// Spring上下文未启动时，直接运行
		if (ContextUtil.isContextUnusable(this.beanFactory)) {
			this.delegate.execute(command);
			return;
		}

		if (this.tracer == null) {
			try {
				this.tracer = this.beanFactory.getBean(Tracer.class);
			} catch (NoSuchBeanDefinitionException e) {
				this.delegate.execute(command);
				return;
			}
		}
		// 将Runnable包装为TraceRunnable，用于在运行时生成Span
		this.delegate.execute(new TraceRunnable(this.tracer, spanNamer(), command, this.beanName));
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
			} catch (NoSuchBeanDefinitionException e) {
				log.warn("SpanNamer bean not found - will provide a manually created instance");
				return new DefaultSpanNamer();
			}
		}
		return this.spanNamer;
	}

}
