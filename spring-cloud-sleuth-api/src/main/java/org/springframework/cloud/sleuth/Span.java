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

import org.springframework.cloud.sleuth.propagation.Propagator;

/**
 * 此API深受Brave影响，其部分文档直接取自Brave。
 *
 * <p>{@link Span}代表一个需要被启动和停止的工作单元，包含了时间信息、时间和标签。
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public interface Span extends SpanCustomizer {

	/**
	 * @return 当没有执行记录和未上报信息到外部系统时，返回{@code true}。然而该{@link Span}可以被
	 * 注入到传出请求。使用该标识避免执行昂贵的计算。
	 */
	boolean isNoop();

	/**
	 * @return 返回对应该 {@link Span}的{@link TraceContext}
	 */
	TraceContext context();

	/**
	 * 开始并返回当前{@link Span}
	 * @return this span
	 */
	Span start();

	/**
	 * 设置{@link Span}的名称，并返回当前{@link Span}
	 * @param name name to set on the span
	 * @return this span
	 */
	Span name(String name);

	/**
	 * 设置{@link Span}的事件，并返回当前{@link Span}
	 * @param value 设置到该{@link Span}的事件名称
	 * @return this span
	 */
	Span event(String value);

	/**
	 * 设置{@link Span}的标签，并返回当前{@link Span}
	 * @param key 标签键
	 * @param value 标签值
	 * @return this span
	 */
	Span tag(String key, String value);

	/**
	 * 记录当前{@link Span}的异常，并返回当前{@link Span}
	 *
	 * @param throwable to record
	 * @return this span
	 */
	Span error(Throwable throwable);

	/**
	 * 结束当前{@link Span}，如果非{@link #isNoop()}，{@link Span}将停止并记录。
	 */
	void end();

	/**
	 * 结束当前{@link Span}，{@link Span}将被停止当不会被记录。
	 */
	void abandon();

	/**
	 * 记录当前{@link Span}的远程服务名称，并返回当前{@link Span}
	 *
	 * @param remoteServiceName 远程服务名称
	 * @return this span
	 * @since 3.0.3
	 */
	default Span remoteServiceName(String remoteServiceName) {
		return this;
	}

	/**
	 * 记录当前{@link Span}的远程路径，并返回当前{@link Span}
	 *
	 * @param ip 远程ip
	 * @param port 远程端口
	 * @return this span
	 * @since 3.1.0
	 */
	default Span remoteIpAndPort(String ip, int port) {
		return this;
	}

	/**
	 * {@link Span}的类型。可以用于指定Span间除父子关系外的其它关系。
	 *
	 * <p>从OpenTelemetry获取的枚举文档
	 */
	enum Kind {

		/**
		 * 指示Span涵盖服务器端对RPC或其它远程请求的处理。
		 */
		SERVER,

		/**
		 * 指示Span涵盖RPC或其它远程请求的客户端包装器。
		 */
		CLIENT,

		/**
		 * 指示Span描述生产者发送消息给Broker。不同于Client和Server，生产者和消费者Span之间没有直接的关键路径延迟关系。
		 */
		PRODUCER,

		/**
		 * 指示Span描述消费者从Broker接受消息。不同于Client和Server，生产者和消费者Span之间没有直接的关键路径延迟关系。
		 */
		CONSUMER

	}

	/**
	 * 用于在某些场景下处理{@link Propagator#extract(Object, Propagator.Getter)}时，
	 * 想要创建一个尚未启动的{@link Span}，但它具有很强的可配置性(当span已经启动时，某些选项无法配置)。
	 * 我们可以使用构建器来实现这一点。
	 *
	 * <p>受到OpenZipkin和OpenTelemetry API的启发。
	 */
	interface Builder {

		/**
		 * 设置构建Span的父级
		 *
		 * @param context parent's context
		 * @return this
		 */
		Builder setParent(TraceContext context);

		/**
		 * 设置构建Span不存在父级
		 *
		 * @return this
		 */
		Builder setNoParent();

		/**
		 * 设置构建Span的名称
		 *
		 * @param name span name
		 * @return this
		 */
		Builder name(String name);

		/**
		 * 设置Span的事件名称
		 *
		 * @param value event value
		 * @return this
		 */
		Builder event(String value);

		/**
		 * 设置Span的标签
		 *
		 * @param key tag key
		 * @param value tag value
		 * @return this
		 */
		Builder tag(String key, String value);

		/**
		 * 设置Span的错误信息
		 *
		 * @param throwable error to set
		 * @return this
		 */
		Builder error(Throwable throwable);

		/**
		 * 设置Span的类型
		 *
		 * @param spanKind kind of the span
		 * @return this
		 */
		Builder kind(Span.Kind spanKind);

		/**
		 * 设置Span的远程服务名称
		 *
		 * @param remoteServiceName remote service name
		 * @return this
		 */
		Builder remoteServiceName(String remoteServiceName);

		/**
		 * 设置Span的远程URL
		 *
		 * @param ip remote service ip
		 * @param port remote service port
		 * @return this
		 */
		default Builder remoteIpAndPort(String ip, int port) {
			return this;
		}

		/**
		 * 构建并启动Span
		 *
		 * @return started span
		 */
		Span start();

	}

}
