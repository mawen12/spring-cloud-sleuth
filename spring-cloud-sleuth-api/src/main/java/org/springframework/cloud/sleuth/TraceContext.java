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

import org.springframework.lang.Nullable;

/**
 * 包含跟踪和跨度数据。
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface TraceContext {

	/**
	 * @return Span的跟踪ID
	 */
	String traceId();

	/**
	 * @return 父级Span的ID
	 */
	@Nullable
	String parentId();

	/**
	 * @return Span的ID
	 */
	String spanId();

	/**
	 * @return 采样时为true，不采样时为false，推迟采样时为null
	 */
	Boolean sampled();

	/**
	 * {@link TraceContext}的构建器
	 *
	 * @since 3.1.0
	 */
	interface Builder {

		/**
		 * 设置Span的Trace ID
		 *
		 * @param traceId trace id
		 * @return this
		 */
		TraceContext.Builder traceId(String traceId);

		/**
		 * 设置Span的父级的ID
		 *
		 * @param parentId parent trace id
		 * @return this
		 */
		TraceContext.Builder parentId(String parentId);

		/**
		 * 设置Span的ID
		 *
		 * @param spanId span id
		 * @return this
		 */
		TraceContext.Builder spanId(String spanId);

		/**
		 * 设置Span的采集开关
		 *
		 * @param sampled if span is sampled
		 * @return this
		 */
		TraceContext.Builder sampled(Boolean sampled);

		/**
		 * 构建{@link TraceContext}
		 *
		 * @return trace context
		 */
		TraceContext build();

	}

}
