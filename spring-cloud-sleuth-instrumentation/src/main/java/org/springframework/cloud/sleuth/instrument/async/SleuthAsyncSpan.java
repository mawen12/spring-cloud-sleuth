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

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

/**
 * 代表Spring Cloud Sleuth 异步的Span
 *
 * <p>支持{@link org.springframework.scheduling.annotation.Async}注解
 * <p>支持{@link java.util.concurrent.Executor}
 */
enum SleuthAsyncSpan implements DocumentedSpan {

	/**
	 * 包装{@link org.springframework.scheduling.annotation.Async}注解的Span，可以继续使用现有的注解，如果不存在则创建一个新的注解
	 */
	ASYNC_ANNOTATION_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

		/**
		 * @return 只允许存在名为{@code class}和{@code method}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

	},

	/**
	 * 每当Runnable需要被检测时，就会创建Span
	 */
	ASYNC_RUNNABLE_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

	},

	/**
	 * 每当Callable需要被检测时，就会创建Span
	 */
	ASYNC_CALLABLE_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

	};

	enum Tags implements TagKey {

		/**
		 * 使用{@link org.springframework.scheduling.annotation.Async}注解的方法的类名
		 */
		CLASS {
			@Override
			public String getKey() {
				return "class";
			}
		},

		/**
		 * 使用{@link org.springframework.scheduling.annotation.Async}注解的方法名
		 */
		METHOD {
			@Override
			public String getKey() {
				return "method";
			}
		}

	}

}
