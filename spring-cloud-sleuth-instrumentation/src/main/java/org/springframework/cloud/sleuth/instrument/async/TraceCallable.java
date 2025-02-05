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

import java.util.Collection;
import java.util.concurrent.Callable;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;

/**
 * 在线程间传递Span的Callable。Span名称取自传递值或{@link SpanNamer}接口。
 *
 * <p>负责对原始Callable进行增强，在其执行周围生成Span
 * <p>该类不应该被用户使用，因为Sleuth不会直接执行该类的列表
 *
 * @see TraceableExecutorService#wrapCallableCollection(Collection)
 *
 * @param <V> - return type from callable
 * @author Spencer Gibb
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
// public as most types in this package were documented for use
public class TraceCallable<V> implements Callable<V> {

	/**
	 * 由于我们不知道确切的操作名称，因此为Span提供一个默认的名称。
	 */
	private static final String DEFAULT_SPAN_NAME = "async";

	/**
	 * 跟踪器
	 */
	private final Tracer tracer;

	/**
	 * 原始的Callable
	 */
	private final Callable<V> delegate;

	/**
	 * 父级Span
	 */
	private final Span parent;

	/**
	 * Span名称
	 */
	private final String spanName;

	public TraceCallable(Tracer tracer, SpanNamer spanNamer, Callable<V> delegate) {
		this(tracer, spanNamer, delegate, null);
	}

	public TraceCallable(Tracer tracer, SpanNamer spanNamer, Callable<V> delegate, String name) {
		this.tracer = tracer;
		this.delegate = delegate;
		this.parent = tracer.currentSpan();
		// 未指定Span名称时，使用生成器来生成
		this.spanName = name != null ? name : spanNamer.name(delegate, DEFAULT_SPAN_NAME);
	}

	@Override
	public V call() throws Exception {
		// 以当前Span作为父级构造一个子级Span
		Span childSpan = SleuthAsyncSpan.ASYNC_CALLABLE_SPAN.wrap(this.tracer.nextSpan(this.parent)).name(this.spanName);
		// 启动Span，并设置为当前Span
		try (Tracer.SpanInScope ws = this.tracer.withSpan(childSpan.start())) {
			// 运行原始的Callable
			return this.delegate.call();
		} catch (Exception | Error ex) {
			// 捕获异常
			childSpan.error(ex);
			throw ex;
		}
		finally {
			// 结束范围
			childSpan.end();
		}
	}

	/**
	 * @return delegate {@link Callable}
	 */
	public Callable<V> getDelegate() {
		return delegate;
	}

}
