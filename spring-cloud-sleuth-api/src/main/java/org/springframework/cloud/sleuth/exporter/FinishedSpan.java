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

package org.springframework.cloud.sleuth.exporter;

import java.util.Collection;
import java.util.Map;

import org.springframework.cloud.sleuth.Span;
import org.springframework.lang.Nullable;

/**
 * 此API受到OpenZipkin Brave {@code MutableSpan}的启发。
 *
 * 代表已完成的Span，并准备发送到外部系统，例如Zipkin
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface FinishedSpan {

	/**
	 * @return Span的名称
	 */
	String getName();

	/**
	 * @return Span的开始时间戳
	 */
	long getStartTimestamp();

	/**
	 * @return Span的结束时间戳
	 */
	long getEndTimestamp();

	/**
	 * @return Span的标签
	 */
	Map<String, String> getTags();

	/**
	 * @return Span的事件作为时间戳到值的映射
	 */
	Collection<Map.Entry<Long, String>> getEvents();

	/**
	 * @return Span的ID
	 */
	String getSpanId();

	/**
	 * @return Span的父级ID
	 */
	@Nullable
	String getParentId();

	/**
	 * @return Span的远程ip
	 */
	@Nullable
	String getRemoteIp();

	/**
	 * @return Span的本地ip
	 */
	@Nullable
	default String getLocalIp() {
		return null;
	}

	/**
	 * @return Span的远程端口
	 */
	int getRemotePort();

	/**
	 * @return Span的Tracer的ID
	 */
	String getTraceId();

	/**
	 * @return 关联异常
	 */
	@Nullable
	Throwable getError();

	/**
	 * @return Span的类型
	 */
	Span.Kind getKind();

	/**
	 * @return Span的远程服务名称
	 */
	@Nullable
	String getRemoteServiceName();

}
