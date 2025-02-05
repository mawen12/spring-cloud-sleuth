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
 * 可以对自身执行断言的{@link Span}。
 *
 * <p>负责执行其它断言，例如允许的名称、标签、事件验证，并在报告时确定跨度是否已首先启动。
 *
 * <p>你需要通过系统属性或环境变量启用断言才能开始中断测试或生产代码。查看{@link DocumentedSpanAssertions}获取更多信息。
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface AssertingSpan extends Span {

	/**
	 * @return 包含Span配置的 {@link DocumentedSpan}
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * @return 原始的Span
	 */
	Span getDelegate();

	/**
	 * @return {@code true} 在已启动时
	 */
	default boolean isStarted() {
		return false;
	}

	@Override
	default AssertingSpan tag(String key, String value) {
		// 校验key合法
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().tag(key, value);
		return this;
	}

	/**
	 * Tags a span via {@link TagKey}.
	 * @param key tag key
	 * @param value tag value
	 * @return this for chaining
	 */
	default AssertingSpan tag(TagKey key, String value) {
		// 校验key合法
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpan event(String value) {
		// 校验事件合法
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().event(value);
		return this;
	}

	/**
	 * Annotates with an event via {@link EventValue}.
	 * @param value event value
	 * @return this for chaining
	 */
	default AssertingSpan event(EventValue value) {
		// 校验事件合法
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpan name(String name) {
		// 校验名称合法
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().name(name);
		return this;
	}

	@Override
	default boolean isNoop() {
		return getDelegate().isNoop();
	}

	@Override
	default TraceContext context() {
		return getDelegate().context();
	}

	@Override
	default AssertingSpan start() {
		// 启动Span
		getDelegate().start();
		return this;
	}

	@Override
	default AssertingSpan error(Throwable throwable) {
		// 设置异常信息
		getDelegate().error(throwable);
		return this;
	}

	@Override
	default void end() {
		// 校验Span已经启动
		DocumentedSpanAssertions.assertThatSpanStartedBeforeEnd(this);
		// 结束Span
		getDelegate().end();
	}

	@Override
	default void abandon() {
		getDelegate().abandon();
	}

	@Override
	default AssertingSpan remoteServiceName(String remoteServiceName) {
		// 设置远程服务信息
		getDelegate().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	default Span remoteIpAndPort(String ip, int port) {
		// 设置远程ip和端口
		getDelegate().remoteIpAndPort(ip, port);
		return this;
	}

	/**
	 * @param documentedSpan span configuration
	 * @param span span to wrap in assertions
	 * @return asserting span
	 */
	static AssertingSpan of(DocumentedSpan documentedSpan, Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (AssertingSpan) span;
		}
		return new ImmutableAssertingSpan(documentedSpan, span);
	}

	/**
	 * @param documentedSpan span configuration
	 * @param span span to wrap in assertions
	 * @return asserting span
	 */
	static AssertingSpan continueSpan(DocumentedSpan documentedSpan, Span span) {
		AssertingSpan assertingSpan = of(documentedSpan, span);
		if (assertingSpan == null) {
			return null;
		}
		((ImmutableAssertingSpan) assertingSpan).isStarted = true;
		return assertingSpan;
	}

	/**
	 * 返回底层委托，在需要强制转换时使用
	 *
	 * @param span span to check for wrapping
	 * @param <T> type extending a span
	 * @return unwrapped object
	 */
	static <T extends Span> T unwrap(Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (T) ((AssertingSpan) span).getDelegate();
		}
		return (T) span;
	}

}
