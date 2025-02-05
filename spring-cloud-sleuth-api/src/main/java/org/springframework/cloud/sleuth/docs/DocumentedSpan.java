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
 * 为了通过枚举而不是字符串来描述Span，可以使用此接口返回跨度的所有特征。
 * 在Spring Cloud Sleuth中，我们分析来源并重用此信息来构建已知Span，名称，标签，事件的表格。
 *
 * <p>我们可以为所有创建的Span生成文档，当需要满足某些要求：
 * <ul>
 *     <li>
 *         跨度分组在枚举中 - 枚举实现 {@link DocumentedSpan}，如果Span包含{@link TagKey}和{@link EventValue}，
 *     		则需要将它们声明为嵌套枚举，{@link #getTagKeys()}和{@link #getEvents()}需要调用嵌套枚举的{@code Enum#values()}来检索允许的键/事件的数组
 *     </li>
 * </ul>
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface DocumentedSpan {

	/**
	 * @return Span名称
	 */
	String getName();

	/**
	 * @return 允许的标签键
	 */
	default TagKey[] getTagKeys() {
		return new TagKey[0];
	}

	/**
	 * @return 允许的事件
	 */
	default EventValue[] getEvents() {
		return new EventValue[0];
	}

	/**
	 * 返回需要用于事件和标签的前缀。例如：{@code foo.}将要求标签和事件具有{@code foo}前缀。
	 * 例如标签: {@code foo.bar=true}
	 * 例如事件：{@code foo.started}
	 *
	 * @return 所需的标签前缀
	 */
	default String prefix() {
		return "";
	}

	/**
	 * 断言标签、名称、和允许的事件
	 *
	 * @param span to wrap
	 * @return wrapped span
	 */
	default AssertingSpan wrap(Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (AssertingSpan) span;
		}
		return AssertingSpan.of(this, span);
	}

	/**
	 * 断言标签、名称、和允许的事件
	 *
	 * @param span to wrap
	 * @return wrapped span
	 */
	default AssertingSpanCustomizer wrap(SpanCustomizer span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanCustomizer) {
			return (AssertingSpanCustomizer) span;
		}
		return AssertingSpanCustomizer.of(this, span);
	}

	/**
	 * 断言标签、名称、和允许的事件
	 *
	 * @param span builder to wrap
	 * @return wrapped span
	 */
	default AssertingSpanBuilder wrap(Span.Builder span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanBuilder) {
			return (AssertingSpanBuilder) span;
		}
		return AssertingSpanBuilder.of(this, span);
	}

}
