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
import org.springframework.cloud.sleuth.SpanCustomizer;

/**
 * 可以对自身执行断言的{@link SpanCustomizer}
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface AssertingSpanCustomizer extends SpanCustomizer {

	/**
	 * @return 包含Span配置的 {@link DocumentedSpan}
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * @return 被包装的 {@link SpanCustomizer}
	 */
	SpanCustomizer getDelegate();

	@Override
	default AssertingSpanCustomizer tag(String key, String value) {
		// 校验标签合法
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
	default AssertingSpanCustomizer tag(TagKey key, String value) {
		// 校验标签合法
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpanCustomizer event(String value) {
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
	default AssertingSpanCustomizer event(EventValue value) {
		// 校验事件合法
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpanCustomizer name(String name) {
		// 校验名称合法
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		// 设置到原始的Span上
		getDelegate().name(name);
		return this;
	}

	/**
	 * @param documentedSpan span configuration
	 * @param span span to wrap in assertions
	 * @return asserting span customizer
	 */
	static AssertingSpanCustomizer of(DocumentedSpan documentedSpan, SpanCustomizer span) {
		if (span instanceof AssertingSpanCustomizer) {
			return (AssertingSpanCustomizer) span;
		}
		return new ImmutableAssertingSpanCustomizer(documentedSpan, span);
	}

	/**
	 * Returns the underlying delegate. Used when casting is necessary.
	 * @param span span to check for wrapping
	 * @param <T> type extending a span
	 * @return unwrapped object
	 */
	static <T extends SpanCustomizer> T unwrap(SpanCustomizer span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanCustomizer) {
			return (T) ((AssertingSpanCustomizer) span).getDelegate();
		}
		return (T) span;
	}

}
