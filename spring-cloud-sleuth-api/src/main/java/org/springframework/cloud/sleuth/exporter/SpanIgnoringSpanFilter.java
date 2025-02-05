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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;

/**
 * 通过名称忽略Span的{@link SpanFilter}实现。
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class SpanIgnoringSpanFilter implements SpanFilter {

	private static final Log log = LogFactory.getLog(SpanIgnoringSpanFilter.class);

	/**
	 * Span要跳过的名称模式列表
	 */
	private final List<String> spanNamePatternsToSkip;

	/**
	 * 额外的Span要忽略的名称模式列表
	 */
	private final List<String> additionalSpanNamePatternsToIgnore;

	/**
	 * 静态缓存
	 */
	static final Map<String, Pattern> cache = new ConcurrentHashMap<>();

	public SpanIgnoringSpanFilter(List<String> spanNamePatternsToSkip,
			List<String> additionalSpanNamePatternsToIgnore) {
		this.spanNamePatternsToSkip = spanNamePatternsToSkip;
		this.additionalSpanNamePatternsToIgnore = additionalSpanNamePatternsToIgnore;
	}

	private List<Pattern> spanNamesToIgnore() {
		return spanNames()
				.stream()
				.map(regex -> cache.computeIfAbsent(regex, Pattern::compile))
				.collect(Collectors.toList());
	}

	/**
	 * 整合{@link #spanNamePatternsToSkip}和{@link #additionalSpanNamePatternsToIgnore}
	 *
	 * @return
	 */
	private List<String> spanNames() {
		List<String> spanNamesToIgnore = new ArrayList<>(this.spanNamePatternsToSkip);
		spanNamesToIgnore.addAll(this.additionalSpanNamePatternsToIgnore);
		return spanNamesToIgnore;
	}

	@Override
	public boolean isExportable(FinishedSpan span) {
		// 获取Span要跳过的名称模式列表
		List<Pattern> spanNamesToIgnore = spanNamesToIgnore();
		// 获取Span的名称
		String name = span.getName();
		if (StringUtils.hasText(name) && spanNamesToIgnore.stream().anyMatch(p -> p.matcher(name).matches())) {
			if (log.isDebugEnabled()) {
				log.debug("Will ignore a span with name [" + name + "]");
			}
			// 如果Span的名称存在，并与任意要跳过的名称模式列表匹配，则代表不需要上报
			return false;
		}
		return true;
	}

}
