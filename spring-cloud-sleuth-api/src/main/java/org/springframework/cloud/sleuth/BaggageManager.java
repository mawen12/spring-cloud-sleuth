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

import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * 管理{@link BaggageInScope}实体。检索/创建行李实体后将其放入范围内。
 *
 * <p>{@link org.springframework.cloud.sleuth.CurrentTraceContext.Scope}必须关闭
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface BaggageManager {

	/**
	 * @return 返回给定范围内所有的行李实体映射
	 */
	Map<String, String> getAllBaggage();

	/**
	 * 检索给定名称的{@link BaggageInScope}
	 *
	 * @param name 行李名称
	 * @return {@link BaggageInScope}，如果不存在返回null
	 */
	@Nullable
	BaggageInScope getBaggage(String name);

	/**
	 * 检索给定名称的{@link BaggageInScope}
	 *
	 * @param traceContext trace context with baggage attached to it
	 * @param name baggage name
	 * @return baggage or {@code null} if not present
	 */
	@Nullable
	BaggageInScope getBaggage(TraceContext traceContext, String name);

	/**
	 * 如果给定名称对应的不存在，则创建一个新的{@link BaggageInScope}，否则返回已存在的。
	 *
	 * @param name baggage name
	 * @return new or already created baggage
	 */
	BaggageInScope createBaggage(String name);

	/**
	 * 如果给定名称对应的不存在，则创建一个新的{@link BaggageInScope}，否则返回已存在的。
	 *
	 * @param name baggage name
	 * @param value baggage value
	 * @return new or already created baggage
	 */
	BaggageInScope createBaggage(String name, String value);

}
