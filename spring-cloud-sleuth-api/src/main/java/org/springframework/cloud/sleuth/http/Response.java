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

package org.springframework.cloud.sleuth.http;

import java.util.Collection;

import org.springframework.cloud.sleuth.Span;
import org.springframework.lang.Nullable;

/**
 * 此API取自 OpenZipkin Brave。
 *
 * <p>用于解析和采样的抽象类型响应
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Response {

	/**
	 * @return 响应头名称列表
	 */
	Collection<String> headerNames();

	/**
	 * @return 用于描述目标和请求类型的Span类型
	 */
	Span.Kind spanKind();

	/**
	 * @return 关联请求
	 */
	@Nullable
	Request request();

	/**
	 * @return 发生的异常或者 {@code null}当未发生
	 */
	@Nullable
	Throwable error();

	/**
	 * @return 底层请求对象或 {@code null}
	 */
	Object unwrap();

}
