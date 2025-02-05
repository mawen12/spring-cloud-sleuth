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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于为Span提供名称的注解。应在为所有自定义{@link Runnable}和{@link java.util.concurrent.Callable}类添加该注解，
 * 以便检测逻辑能够确定如何命名Span。
 *
 * <p>示例，将生成一个名为{@code custom-operation}的Span
 * <pre>{@code
 * 	@SpanName("custom-operation)
 * 	class CustomRunnable implements Runnable {
 * 		@Override
 * 		public void run() {
 * 			// latency of this method will be recorded in a span named "custom-operation"
 * 		}
 * 	}
 * }</pre>
 *
 * <p>如果未提供注解，将使用{@link Object#toString()}作为Span的名称
 * <pre>{@code
 * 	return new Runnable() {
 * 	    -- snip --
 *
 * 		@Override
 * 		public String toString() {
 * 		 	return "custom-operation";
 * 		}
 * 	}
 * }</pre>
 *
 * <p>从{@code 1.3.0}开始，支持将{@link SpanName}与{@link org.springframework.scheduling.annotation.Async}一起使用加在方法上
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SpanName {

	/**
	 * 在运行时被解析的Span名称
	 *
	 * @return - value of the span name.
	 */
	String value();

}
