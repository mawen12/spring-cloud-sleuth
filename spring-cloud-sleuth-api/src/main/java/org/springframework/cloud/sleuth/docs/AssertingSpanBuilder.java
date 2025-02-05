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

package org.springframework.cloud.sleuth.docs;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * 可以对自身执行断言的{@link Span.Builder}
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface AssertingSpanBuilder extends Span.Builder {

	/**
	 * @return 包含Span配置的 {@link DocumentedSpan}
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * @return 底层委托的 {@link Span.Builder}
	 */
	Span.Builder getDelegate();

	@Override
	default AssertingSpanBuilder tag(String key, String value) {
		// 校验key合法
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().tag(key, value);
		return this;
	}

	/**
	 * Sets a tag on a span.
	 * @param key tag key
	 * @param value tag
	 * @return this, for chaining
	 */
	default AssertingSpanBuilder tag(TagKey key, String value) {
		// 校验key合法
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpanBuilder event(String value) {
		// 校验事件合法
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().event(value);
		return this;
	}

	/**
	 * Sets an event on a span.
	 * @param value event
	 * @return this, for chaining
	 */
	default AssertingSpanBuilder event(EventValue value) {
		// 校验事件合法
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpanBuilder name(String name) {
		// 校验名称合法
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().name(name);
		return this;
	}

	@Override
	default AssertingSpanBuilder error(Throwable throwable) {
		// 设置异常信息
		getDelegate().error(throwable);
		return this;
	}

	@Override
	default AssertingSpanBuilder remoteServiceName(String remoteServiceName) {
		// 设置远程服务信息
		getDelegate().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	default Span.Builder remoteIpAndPort(String ip, int port) {
		// 设置远程ip和端口
		getDelegate().remoteIpAndPort(ip, port);
		return this;
	}

	@Override
	default AssertingSpanBuilder setParent(TraceContext context) {
		// 设置父级
		getDelegate().setParent(context);
		return this;
	}

	@Override
	default AssertingSpanBuilder setNoParent() {
		// 清除父级
		getDelegate().setNoParent();
		return this;
	}

	@Override
	default AssertingSpanBuilder kind(Span.Kind spanKind) {
		// 设置类型
		getDelegate().kind(spanKind);
		return this;
	}

	@Override
	default AssertingSpan start() {
		// 启动Span
		Span span = getDelegate().start();
		// 获取可文档化的Span
		DocumentedSpan documentedSpan = getDocumentedSpan();
		// 返回可断言的Span
		return new AssertingSpan() {
			@Override
			public DocumentedSpan getDocumentedSpan() {
				return documentedSpan;
			}

			@Override
			public Span getDelegate() {
				return span;
			}

			@Override
			public boolean isStarted() {
				return true;
			}

			@Override
			public String toString() {
				return getDelegate().toString();
			}
		};
	}

	/**
	 * @param documentedSpan span configuration
	 * @param builder builder to wrap in assertions
	 * @return asserting span builder
	 */
	static AssertingSpanBuilder of(DocumentedSpan documentedSpan, Span.Builder builder) {
		if (builder == null) {
			return null;
		}
		else if (builder instanceof AssertingSpanBuilder) {
			return (AssertingSpanBuilder) builder;
		}
		return new ImmutableAssertingSpanBuilder(documentedSpan, builder);
	}

}
