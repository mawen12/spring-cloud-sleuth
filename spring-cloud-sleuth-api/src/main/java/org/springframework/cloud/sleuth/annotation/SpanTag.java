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

package org.springframework.cloud.sleuth.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * 用于向Span添加标签。
 *
 * <p>有三种向Span添加标签的方法。均被注解值所控制。
 * <ul>
 *     <li>第一步：如果设置了{@link #resolver()}，则使用该类来处理；否则执行第二步</li>
 *     <li>第二步：如果设置了{@link #expression()}，则使用{@link TagValueExpressionResolver}来处理；否则执行第三步</li>
 *     <li>第三步：获取{@link #toString()}作为参数值</li>
 * </ul>
 *
 * @author Christian Schwerdtfeger
 * @see org.springframework.cloud.sleuth.instrument.annotation.SpanTagAnnotationHandler#resolveTagValue(SpanTag, Object)
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.PARAMETER)
public @interface SpanTag {

	/**
	 * @return - 被创建的标签键的名称
	 */
	@AliasFor("key")
	String value() default "";

	/**
	 * @return - 被创建的标签键的名称
	 */
	@AliasFor("value")
	String key() default "";

	/**
	 * @return - 执行SPEL表达式来计算标签值，如果没有设置{@link #resolver()}的值，将会进行解析
	 */
	String expression() default "";

	/**
	 * @return - 使用此Bean来解析标签值，具有最高的优先级
	 */
	Class<? extends TagValueResolver> resolver() default NoOpTagValueResolver.class;

}
