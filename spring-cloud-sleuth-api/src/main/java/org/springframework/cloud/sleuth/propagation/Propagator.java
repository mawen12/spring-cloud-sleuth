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

package org.springframework.cloud.sleuth.propagation;

import java.util.List;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.lang.Nullable;

/**
 * 灵感来自于OpenZipkin Brave和OpenTelemetry，大部分文档直接来自于OpenTelemetry.
 *
 * <p>将值作为文本注入并提取到跨进程边界带内传输的载体中。编码应符合HTTP标头字段语义。
 * 值通常被编码为RPC/HTTP请求标头。
 *
 * @author OpenZipkin Brave Authors
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Propagator {

	/**
	 * @return 返回带有跟踪信息的标头集合
	 */
	List<String> fields();

	/**
	 * 将值注入下游，例如作为HTTP标头。载体可以为null，以便使用{@link Setter}的lambda调用此方法。
	 * 在这种情况下，该null将传递给{@link Setter}实现。
	 *
	 * @param context the {@code Context} containing the value to be injected.
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request.
	 * @param setter invoked for each propagation key to add or remove.
	 * @param <C> carrier of propagation fields, such as an http request
	 */
	<C> void inject(TraceContext context, @Nullable C carrier, Setter<C> setter);

	/**
	 * 从上游提取值，例如HTTP标头。
	 *
	 * <p>如果值不能被解析，将由底层实现决定设置一个表示空值、无效值或有效值的对象。实现不得设置{@code null}。
	 *
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request.
	 * @param getter invoked for each propagation key to get.
	 * @param <C> carrier of propagation fields, such as an http request.
	 * @return the {@code Context} containing the extracted value.
	 */
	<C> Span.Builder extract(C carrier, Getter<C> getter);

	/**
	 * 允许{@code TextMapPropagator}将传播字段设置到载体中的类
	 *
	 * <p>{@link Setter}是一个无状态，且允许保存为常量以避免运行时分配
	 *
	 * @param <C> carrier of propagation fields, such as an http request
	 * @since 0.1.0
	 */
	interface Setter<C> {

		/**
		 * 用给定值替换传播的字段
		 *
		 * <p>例如，用于{@link java.net.HttpURLConnection}可能是方法引用{@link java.net.HttpURLConnection#addRequestProperty(String, String)}
		 *
		 * @param carrier holds propagation fields. For example, an outgoing message or
		 * http request. To facilitate implementations as java lambdas, this parameter may
		 * be null.
		 * @param key the key of the field.
		 * @param value the value of the field.
		 */
		void set(@Nullable C carrier, String key, String value);

	}

	/**
	 * 允许{@code TextMapPropagator} 从载体读取传播字段的接口
	 *
	 * <p> {@link Getter}是一个无状态，且允许保存为常量以避免运行时分配
	 *
	 * @param <C> carrier of propagation fields, such as an http request.
	 */
	interface Getter<C> {

		/**
		 * 返回给定传播{@code key}的第一个值或返回{@code null}.
		 *
		 * @param carrier carrier of propagation fields, such as an http request.
		 * @param key the key of the field.
		 * @return the first value of the given propagation {@code key} or returns
		 * {@code null}.
		 */
		@Nullable
		String get(C carrier, String key);

	}

}
