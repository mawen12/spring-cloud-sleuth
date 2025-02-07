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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.Collections;
import java.util.List;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;

import org.springframework.cloud.sleuth.exporter.SpanFilter;
import org.springframework.cloud.sleuth.exporter.SpanReporter;

/**
 * 将{@link SpanFilter}和{@link SpanReporter}合并到{@link SpanHandler}
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class CompositeSpanHandler extends SpanHandler {

	/**
	 * Span过滤列表
	 */
	private final List<SpanFilter> filters;

	/**
	 * Span上报列表
	 */
	private final List<SpanReporter> reporters;

	public CompositeSpanHandler(List<SpanFilter> filters, List<SpanReporter> reporters) {
		this.filters = filters == null ? Collections.emptyList() : filters;
		this.reporters = reporters == null ? Collections.emptyList() : reporters;
	}

	@Override
	public boolean end(TraceContext context, MutableSpan span, Cause cause) {
		if (cause != Cause.FINISHED) {
			// 未结束，返回true
			return true;
		}
		// 是否应该处理Span
		boolean shouldProcess = shouldProcess(span);
		if (!shouldProcess) {
			// Span不可上报，返回false
			return false;
		}
		// 使用父类处理
		shouldProcess = super.end(context, span, cause);
		if (!shouldProcess) {
			// 父类无法处理，返回false
			return false;
		}
		// 按序执行上报
		this.reporters.forEach(r -> r.report(BraveFinishedSpan.fromBrave(span)));
		return true;
	}

	/**
	 * @param span
	 * @return {@link true} Span能够上报
	 */
	private boolean shouldProcess(MutableSpan span) {
		for (SpanFilter exporter : this.filters) {
			if (!exporter.isExportable(BraveFinishedSpan.fromBrave(span))) {
				return false;
			}
		}
		return true;
	}

}
