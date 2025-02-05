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

import org.springframework.lang.Nullable;

/**
 * 此API取自OpenZipkin Brave。
 *
 * <p>用于解析和采样的抽象类型响应，代表一个HTTP响应
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface HttpResponse extends Response {

	@Nullable
	@Override
	default HttpRequest request() {
		return null;
	}

	/**
	 * @return HTTP 方法
	 */
	@Nullable
	default String method() {
		HttpRequest request = request();
		return request != null ? request.method() : null;
	}

	/**
	 * 返回一个类似于{@code /items/:itemId/}的表达式，代表应用端点，常规与标签键"http.route"相关联。
	 * 如果没有路由匹配，则返回{@code ""}空字符串。{@code null}代表该仪器不理解http路由
	 *
	 * @return HTTP route or {@code null} if not set.
	 */
	@Nullable
	default String route() {
		HttpRequest request = request();
		return request != null ? request.route() : null;
	}

	/**
	 * @return HTTP状态吗，如果不可达为0
	 */
	int statusCode();

	/**
	 * @param header header name
	 * @return HTTP标头值或 {@link null}当未设置时
	 */
	default String header(String header) {
		return null;
	}

}
