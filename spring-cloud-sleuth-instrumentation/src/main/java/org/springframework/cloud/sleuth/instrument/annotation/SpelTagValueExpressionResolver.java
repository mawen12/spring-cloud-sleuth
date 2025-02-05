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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.cloud.sleuth.annotation.TagValueExpressionResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * 负责使用SPEL来解析{@link SpanTag#expression()}，如果处理过程中抛出异常，则使用参数值的{@link #toString()}作为替代
 *
 * @author Marcin Grzejszczak
 * @since 1.2.0
 */
public class SpelTagValueExpressionResolver implements TagValueExpressionResolver {

	private static final Log log = LogFactory.getLog(SpelTagValueExpressionResolver.class);

	@Override
	public String resolve(String expression, Object parameter) {
		try {
			// 获取表达式评估上下文
			SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
			// 构建表达式解析器
			ExpressionParser expressionParser = new SpelExpressionParser();
			// 解析生成表达式
			Expression expressionToEvaluate = expressionParser.parseExpression(expression);
			// 解析值
			return expressionToEvaluate.getValue(context, parameter, String.class);
		}
		catch (Exception ex) {
			log.error("Exception occurred while tying to evaluate the SPEL expression [" + expression + "]", ex);
		}
		// 解析失败时，使用参数的{@code toString}
		return parameter.toString();
	}

}
