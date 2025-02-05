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
 * 此API深受Brave影响，其部分文档直接取自Brave。
 *
 * 决定基于请求属性例如HTTP路径来确定是否启动一个新的{@link Tracer}
 *
 * @param <T> type of the input, for example a request or method
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface SamplerFunction<T> {

	/**
	 * 为新的{@link Tracer}返回覆盖后的采样策略
	 *
	 * @param arg parameter to evaluate for a sampling decision. {@code null} input
	 * results in a {@code null} result
	 * @return {@code true} to sample a new trace or {@code false} to deny. {@code null}
	 * defers the decision.
	 */
	@Nullable
	Boolean trySample(@Nullable T arg);

	/**
	 * 总是推迟采样
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return decision deferring sampler function
	 */
	static <T> SamplerFunction<T> deferDecision() {
		return (SamplerFunction<T>) Constants.DEFER_DECISION;
	}

	/**
	 * 永远不采样
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return never sampling sampler function
	 */
	static <T> SamplerFunction<T> neverSample() {
		return (SamplerFunction<T>) Constants.NEVER_SAMPLE;
	}

	/**
	 * 总是采样
	 *
	 * @param <T> type of the input, for example a request or method
	 * @return always sampling sampler function
	 */
	static <T> SamplerFunction<T> alwaysSample() {
		return (SamplerFunction<T>) Constants.ALWAYS_SAMPLE;
	}

	/**
	 * Constant {@link SamplerFunction}s.
	 */
	enum Constants implements SamplerFunction<Object> {

		/**
		 * 总是推迟采样的决策
		 */
		DEFER_DECISION {
			@Override
			public Boolean trySample(Object request) {
				return null;
			}

			@Override
			public String toString() {
				return "DeferDecision";
			}
		},

		/**
		 * 永远不采样
		 */
		NEVER_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return false;
			}

			@Override
			public String toString() {
				return "NeverSample";
			}
		},

		/**
		 * 总是采样
		 */
		ALWAYS_SAMPLE {
			@Override
			public Boolean trySample(Object request) {
				return true;
			}

			@Override
			public String toString() {
				return "AlwaysSample";
			}
		}

	}

}
