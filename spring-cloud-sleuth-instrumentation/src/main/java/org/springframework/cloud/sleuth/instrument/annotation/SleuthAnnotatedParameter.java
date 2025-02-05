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

import org.springframework.cloud.sleuth.annotation.SpanTag;

/**
 * 保留具有注解的方法参数信息
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class SleuthAnnotatedParameter {

	/**
	 * 参数索引
	 */
	final int parameterIndex;

	/**
	 * {@link SpanTag}注解
	 */
	final SpanTag annotation;

	/**
	 * 参数值
	 */
	final Object argument;

	SleuthAnnotatedParameter(int parameterIndex, SpanTag annotation, Object argument) {
		this.parameterIndex = parameterIndex;
		this.annotation = annotation;
		this.argument = argument;
	}

}
