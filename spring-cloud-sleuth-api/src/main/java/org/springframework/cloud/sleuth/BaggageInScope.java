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

import org.springframework.lang.Nullable;

/**
 * 灵感来自于 OpenZipkin Brave的{@code BaggageField}，由于某些跟踪器实现需要将范围包裹在baggage周围，
 * 因此必须关闭baggage以免范围泄漏。某些跟踪器实现使baggage不可变（如OpenTelemetry)，因此当值更新时，
 * 它们可能会创建新的范围（其它将返回相同的范围，如OpenZipkin Brave）。
 *
 * <p>代表单件行李实体
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface BaggageInScope extends Closeable {

	/**
	 * @return 行李实体的名称
	 */
	String name();

	/**
	 * @return value of the baggage entry or {@code null} if not set.
	 */
	@Nullable
	String get();

	/**
	 * 从给定的{@link TraceContext}检索行李实体的值
	 *
	 * @param traceContext context containing baggage
	 * @return value of the baggage entry or {@code null} if not set.
	 */
	@Nullable
	String get(TraceContext traceContext);

	/**
	 * 设置行李的值
	 *
	 * @param value to set
	 * @return new scope
	 */
	BaggageInScope set(String value);

	/**
	 * 设置给定{@link TraceContext}的行李值
	 *
	 * @param traceContext context containing baggage
	 * @param value to set
	 * @return new scope
	 */
	BaggageInScope set(TraceContext traceContext, String value);

	/**
	 * 设置范围内的当前行李
	 *
	 * @return this in scope
	 */
	BaggageInScope makeCurrent();

	@Override
	void close();

}
