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

import java.io.Closeable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link Span}及其对应的{@link Tracer.SpanInScope}的容器对象。
 *
 * <p>用于在关闭{@link SpanAndScope}时，关闭{@link Tracer.SpanInScope}和结束{@link Span}
 *
 * @author Marcin Grzejszczak
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class SpanAndScope implements Closeable {

	private static final Log log = LogFactory.getLog(SpanAndScope.class);
	/**
	 * Sleuth的Span
	 */
	private final Span span;

	/**
	 * Span范围对象
	 */
	private final Tracer.SpanInScope scope;

	public SpanAndScope(Span span, Tracer.SpanInScope scope) {
		this.span = span;
		this.scope = scope;
	}

	public Span getSpan() {
		return this.span;
	}

	public Tracer.SpanInScope getScope() {
		return this.scope;
	}

	@Override
	public String toString() {
		return "SpanAndScope{" + "span=" + this.span + '}';
	}

	@Override
	public void close() {
		// 记录关闭日志
		if (log.isDebugEnabled()) {
			log.debug("Closing span [" + this.span + "], scope is not null [" + (this.scope != null) + "]");
		}
		// 关闭范围
		if (this.scope != null) {
			this.scope.close();
		}
		// 结束Span
		this.span.end();
	}

}
