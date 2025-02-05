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

package org.springframework.cloud.sleuth.instrument.annotation;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.annotation.NewSpanParser;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.util.StringUtils;

/**
 * {@link NewSpanParser}的默认实现，仅解析Span名称
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
public class DefaultSpanCreator implements NewSpanParser {

	private static final Log log = LogFactory.getLog(DefaultSpanCreator.class);

	@Override
	public void parse(MethodInvocation pjp, NewSpan newSpan, Span span) {
		// 解析Span名称，从 NewSpan#name -> Method#name
		String name = newSpan == null || StringUtils.isEmpty(newSpan.name()) ? pjp.getMethod().getName() : newSpan.name();
		// 转换为小写的连字符
		String changedName = SpanNameUtil.toLowerHyphen(name);
		if (log.isDebugEnabled()) {
			log.debug("For the class [" + pjp.getThis().getClass() + "] method " + "[" + pjp.getMethod().getName()
					+ "] will name the span [" + changedName + "]");
		}
		// 设置名称
		span.name(changedName);
	}

}
