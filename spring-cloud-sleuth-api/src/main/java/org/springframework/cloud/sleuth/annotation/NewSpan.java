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
 * 允许创建一个围绕在public方法的Span，如果进程中存在了Tracer，则以存在的Span作为其子级，或者是Trace不存在则创建一个新的Span。
 *
 * <p>使用{@link SpanTag}注释方法参数，参数值最终作为标签值。标签键则取{@link SpanTag#key()}。
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.METHOD)
public @interface NewSpan {

	/**
	 * @return - 被创建的Span名称，默认是用连字符分隔的方法的名称。
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * @return - 被创建的Span名称，默认是用连字符分隔的方法的名称。
	 */
	@AliasFor("name")
	String value() default "";

}
