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

package org.springframework.cloud.sleuth;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.springframework.lang.Nullable;

/**
 * 此API深受Brave影响，其部分文档直接取自Brave。
 *
 * <p>通过将给定Span置于范围内（通常但不总是线程本地范围），可以使其成为当前Span
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface CurrentTraceContext {

	/**
	 * @return 当前 {@link TraceContext} 或在不存在时为{@code null}
	 */
	@Nullable
	TraceContext context();

	/**
	 * 设置当前Span在范围内，直到调用了对应的关闭方法。对于直接抛出或永不关闭结果，这是一个编程错误。使用try-with-resources来避免这种问题。
	 *
	 * @param context span to place into scope or {@code null} to clear the scope
	 * @return the scope with the span set
	 */
	CurrentTraceContext.Scope newScope(@Nullable TraceContext context);

	/**
	 * 类似于{@link #newScope(TraceContext)}，当给定的上下文已经存在于范围中时，返回{@link Scope#NOOP}。
	 *
	 * @param context span to place into scope or {@code null} to clear the scope
	 * @return the scope with the span set
	 */
	CurrentTraceContext.Scope maybeScope(@Nullable TraceContext context);

	/**
	 * 将{@link Callable}包装在Trace表示中
	 *
	 * @param task task to wrap
	 * @param <C> task return type
	 * @return wrapped task
	 */
	<C> Callable<C> wrap(Callable<C> task);

	/**
	 * 将{@link Runnable}包装在Trace表示中
	 *
	 * @param task task to wrap
	 * @return wrapped task
	 */
	Runnable wrap(Runnable task);

	/**
	 * 将{@link Executor}包装在Trace表示中
	 *
	 * @param delegate executor to wrap
	 * @return wrapped executor
	 */
	Executor wrap(Executor delegate);

	/**
	 * 将{@link ExecutorService}包装在Trace表示中
	 *
	 * @param delegate executor service to wrap
	 * @return wrapped executor service
	 */
	ExecutorService wrap(ExecutorService delegate);

	/**
	 * {@link Span}的范围，需要调用{@link #close()}来释放资源，例如清理MDC
	 */
	interface Scope extends Closeable {

		/**
		 * Noop instance.
		 */
		Scope NOOP = () -> {

		};

		@Override
		void close();

	}

}
