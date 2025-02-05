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

/**
 * 此API取自OpenZipkin Brave
 *
 * <p>标准化了一种检测http服务端的方法，特别是鼓励通过{@link HttpRequestParser}和{@link HttpResponseParser}使用可移植定制的方式。
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface HttpServerHandler {

	/**
	 * 根据是否从请求中提取了跟踪上下文，有条件的加入Span或开始新的Tracer。在Span开始之前加入标签。
	 *
	 * @param request HTTP request
	 * @return 服务端侧Span (either joined or a new trace)
	 */
	Span handleReceive(HttpServerRequest request);

	/**
	 * 根据响应或错误分配标签后完成服务端Span
	 *
	 * @param response HTTP response
	 * @param span server side span to end
	 */
	void handleSend(HttpServerResponse response, Span span);

}
