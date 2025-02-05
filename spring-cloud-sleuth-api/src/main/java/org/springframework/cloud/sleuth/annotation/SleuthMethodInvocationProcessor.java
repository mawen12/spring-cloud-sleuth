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

package org.springframework.cloud.sleuth.annotation;

import org.aopalliance.intercept.MethodInvocation;

/**
 * 处理Sleuth注解的合约
 *
 * <p>支持以下注解
 * <ul>
 *     <li>{@link SpanTag}</li>
 *     <li>{@link ContinueSpan}</li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
public interface SleuthMethodInvocationProcessor {

	/**
	 * 执行给定的包含Sleuth注解的方法
	 *
	 * @param invocation 方法调用
	 * @param newSpan {@link NewSpan}注解
	 * @param continueSpan {@link ContinueSpan}注解
	 * @return 方法执行结果
	 * @throws Throwable 运行方法时出现的异常
	 */
	Object process(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable;

}
