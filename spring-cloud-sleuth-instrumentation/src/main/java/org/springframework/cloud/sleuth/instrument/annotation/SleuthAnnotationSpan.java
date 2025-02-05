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

package org.springframework.cloud.sleuth.instrument.annotation;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

/**
 * 代表Spring Cloud Sleuth注解的Span
 *
 * <p>支持{@link org.springframework.cloud.sleuth.annotation.NewSpan}
 * <p>支持{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}
 *
 */
enum SleuthAnnotationSpan implements DocumentedSpan {

	/**
	 * 包装{@link org.springframework.cloud.sleuth.annotation.NewSpan}和{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}注解的Span
	 */
	ANNOTATION_NEW_OR_CONTINUE_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

		/**
		 * @return 只允许存在名称{@code class}和{@code method}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}

		/**
		 * @return 只存在{@code .before}，{@code .after}和{@code .afterFailure}事件
		 */
		@Override
		public EventValue[] getEvents() {
			return Events.values();
		}

	};

	/**
	 * Spring Cloud Sleuth注解相关标签
	 *
	 * @author Marcin Grzejszczak
	 * @since 3.0.3
	 */
	enum Tags implements TagKey {

		/**
		 * 使用{@link org.springframework.cloud.sleuth.annotation.NewSpan}或{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}注解的方法的类名
		 */
		CLASS {
			@Override
			public String getKey() {
				return "class";
			}
		},

		/**
		 * 使用{@link org.springframework.cloud.sleuth.annotation.NewSpan}或{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}注解的方法名
		 */
		METHOD {
			@Override
			public String getKey() {
				return "method";
			}
		}

	}

	enum Events implements EventValue {

		/**
		 * 在执行注解{@link org.springframework.cloud.sleuth.annotation.NewSpan}或{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}之前进行的注解
		 */
		BEFORE {
			@Override
			public String getValue() {
				return "%s.before";
			}
		},

		/**
		 * 在执行注解{@link org.springframework.cloud.sleuth.annotation.NewSpan}或{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}之后进行的注解
		 */
		AFTER {
			@Override
			public String getValue() {
				return "%s.after";
			}
		},

		/**
		 * 在执行注解{@link org.springframework.cloud.sleuth.annotation.NewSpan}或{@link org.springframework.cloud.sleuth.annotation.ContinueSpan}失败时进行的注解
		 */
		AFTER_FAILURE {
			@Override
			public String getValue() {
				return "%s.afterFailure";
			}
		}

	}

}
