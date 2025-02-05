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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.lang.Nullable;

/**
 * 此API取自OpenZipkin Brave
 *
 * <p>标准化了一种检测http客户端的方法，特别是鼓励通过{@link HttpRequestParser}和{@link HttpResponseParser}使用可移植定制的方式。
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface HttpClientHandler {

	/**
	 * 在分配Span名称和标签后启动客户端Span。在返回之前将跟踪上下文注入到请求中。
	 *
	 * <p>在线发送请求之前调用此函数
	 *
	 * @param request to inject the tracing context with
	 * @return 客户端侧Span
	 */
	Span handleSend(HttpClientRequest request);

	/**
	 * 与{@link #handleSend(HttpClientRequest)}类似但存在明确的父级{@link TraceContext}
	 *
	 * @param request to inject the tracing context with
	 * @param parent {@link TraceContext} that is to be the client side span's parent
	 * @return 客户端侧Span
	 */
	Span handleSend(HttpClientRequest request, @Nullable TraceContext parent);

	/**
	 * 根据响应或错误分配标签后完成客户端Span
	 *
	 * @param response the HTTP response
	 * @param span span to be ended
	 */
	void handleReceive(HttpClientResponse response, Span span);

}
