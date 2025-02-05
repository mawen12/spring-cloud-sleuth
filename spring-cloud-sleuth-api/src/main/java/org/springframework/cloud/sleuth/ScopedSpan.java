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

/**
 * 代表当前Span，直到调用{@link ScopedSpan#end()}。
 *
 * <p>此类与{@link Span}方法基本相同，但是其代表运行时的Span，因此不存在start方法。
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface ScopedSpan {

	/**
	 * @return 当没有执行记录和未上报信息到外部系统时，返回{@code true}。然而该{@link Span}可以被
	 * 注入到传出请求。使用该标识避免执行昂贵的计算。
	 */
	boolean isNoop();

	/**
	 * @return 返回对应该 {@link Span}的{@link TraceContext}
	 */
	TraceContext context();

	/**
	 * 设置{@link Span}的名称，并返回当前{@link Span}
	 * @param name name to set on the span
	 * @return this span
	 */
	ScopedSpan name(String name);

	/**
	 * 设置{@link Span}的标签，并返回当前{@link Span}
	 * @param key tag key
	 * @param value tag value
	 * @return this span
	 */
	ScopedSpan tag(String key, String value);

	/**
	 * 设置{@link Span}的事件，并返回当前{@link Span}
	 * @param value event name to set on the span
	 * @return this span
	 */
	ScopedSpan event(String value);

	/**
	 * 记录当前{@link Span}的异常，并返回当前{@link Span}
	 *
	 * @param throwable to record
	 * @return this span
	 */
	ScopedSpan error(Throwable throwable);

	/**
	 * 结束当前{@link Span}，如果非{@link #isNoop()}，{@link Span}将停止并记录。
	 */
	void end();

}
