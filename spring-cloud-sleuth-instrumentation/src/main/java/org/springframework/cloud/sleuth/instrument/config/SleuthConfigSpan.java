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

package org.springframework.cloud.sleuth.instrument.config;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

/**
 * 代表Spring Cloud Sleuth Config的Span
 *
 * <p>支持Spring Cloud Config
 */
enum SleuthConfigSpan implements DocumentedSpan {

	/**
	 * 围绕{@link org.springframework.cloud.config.server.environment.EnvironmentRepository}创建的Span
	 */
	CONFIG_SPAN {
		/**
		 * @return 名称要求：find
		 */
		@Override
		public String getName() {
			return "find";
		}

		/**
		 * @return 只允许存在{@code config.environment.class}和{@code config.environment.method}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return Tags.values();
		}
	};

	enum Tags implements TagKey {

		/**
		 * {@link org.springframework.cloud.config.server.environment.EnvironmentRepository}的实现类
		 */
		ENVIRONMENT_CLASS {
			@Override
			public String getKey() {
				return "config.environment.class";
			}
		},

		/**
		 * {@link org.springframework.cloud.config.server.environment.EnvironmentRepository}的执行方法
		 */
		ENVIRONMENT_METHOD {
			@Override
			public String getKey() {
				return "config.environment.method";
			}
		}

	}

}
