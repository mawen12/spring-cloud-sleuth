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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.SpanCustomizer;
import org.springframework.cloud.sleuth.annotation.NoOpTagValueResolver;
import org.springframework.cloud.sleuth.annotation.SpanTag;
import org.springframework.cloud.sleuth.annotation.TagValueExpressionResolver;
import org.springframework.cloud.sleuth.annotation.TagValueResolver;
import org.springframework.util.StringUtils;

/**
 * 该类负责发现带有Spring Cloud Sleuth注解的所有方法。所有方法意味着如果有一个使用Sleuth注解的接口和实现，
 * 那么该类就能找到它们并合并为一组跟踪信息。
 *
 * <p>这个信息会被用于从带有{@link SpanTag}方法参数上追加到属性标签中。
 * <p>该类会将从接口、抽象类、实现类中来读取方法上的{@link SpanTag}注解信息
 *
 * @author Christian Schwerdtfeger
 * @since 1.2.0
 */
class SpanTagAnnotationHandler {

	private static final Log log = LogFactory.getLog(SpanTagAnnotationHandler.class);

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * Span自定义工具
	 */
	private SpanCustomizer spanCustomizer;

	SpanTagAnnotationHandler(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	void addAnnotatedParameters(MethodInvocation pjp) {
		try {
			// 获取方法
			Method method = pjp.getMethod();
			// 获取最具体的方法
			Method mostSpecificMethod = AopUtils.getMostSpecificMethod(method, pjp.getThis().getClass());
			// 获取方法上的使用了SpanTag注解的参数信息
			List<SleuthAnnotatedParameter> annotatedParameters = SleuthAnnotationUtils.findAnnotatedParameters(mostSpecificMethod, pjp.getArguments());
			// 合并实现和接口上的SpanTag注解信息
			getAnnotationsFromInterfaces(pjp, mostSpecificMethod, annotatedParameters);
			// 合并SpanTag注解信息
			mergeAnnotatedMethodsIfNecessary(pjp, method, mostSpecificMethod, annotatedParameters);
			// 添加到SpanCustomizer上
			addAnnotatedArguments(annotatedParameters);
		}
		catch (SecurityException ex) {
			log.error("Exception occurred while trying to add annotated parameters", ex);
		}
	}

	/**
	 * 将实现上的{@link SpanTag}注解与接口上的{@link SpanTag}进行合并
	 *
	 * @param pjp
	 * @param mostSpecificMethod
	 * @param annotatedParameters
	 */
	private void getAnnotationsFromInterfaces(MethodInvocation pjp, Method mostSpecificMethod, List<SleuthAnnotatedParameter> annotatedParameters) {
		// 获取所有的接口
		Class<?>[] implementedInterfaces = pjp.getThis().getClass().getInterfaces();
		if (implementedInterfaces.length > 0) {
			for (Class<?> implementedInterface : implementedInterfaces) {
				for (Method methodFromInterface : implementedInterface.getMethods()) {
					// 检查方法名称和方法参数是否匹配
					if (methodsAreTheSame(mostSpecificMethod, methodFromInterface)) {
						// 获取接口方法上的参数信息
						List<SleuthAnnotatedParameter> annotatedParametersForActualMethod = SleuthAnnotationUtils.findAnnotatedParameters(methodFromInterface, pjp.getArguments());
						// 合并实现方法和接口方法上的参数信息
						mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
					}
				}
			}
		}
	}

	/**
	 * @param mostSpecificMethod
	 * @param method1
	 * @return {@code true} 当方法名称和方法参数匹配时
	 */
	private boolean methodsAreTheSame(Method mostSpecificMethod, Method method1) {
		return method1.getName().equals(mostSpecificMethod.getName()) && Arrays.equals(method1.getParameterTypes(), mostSpecificMethod.getParameterTypes());
	}

	private void mergeAnnotatedMethodsIfNecessary(MethodInvocation pjp, Method method, Method mostSpecificMethod,
			List<SleuthAnnotatedParameter> annotatedParameters) {
		// 用于处理抽象类和具体类同时存在@NewSpan注解的情况
		if (!method.equals(mostSpecificMethod)) {
			// 获取方法上的SpanTag注解信息
			List<SleuthAnnotatedParameter> annotatedParametersForActualMethod = SleuthAnnotationUtils.findAnnotatedParameters(method, pjp.getArguments());
			// 合并注解信息
			mergeAnnotatedParameters(annotatedParameters, annotatedParametersForActualMethod);
		}
	}

	/**
	 * 合并{@link SpanTag}注解信息
	 *
	 * @param annotatedParametersIndices
	 * @param annotatedParametersIndicesForActualMethod
	 */
	private void mergeAnnotatedParameters(List<SleuthAnnotatedParameter> annotatedParametersIndices, List<SleuthAnnotatedParameter> annotatedParametersIndicesForActualMethod) {
		for (SleuthAnnotatedParameter container : annotatedParametersIndicesForActualMethod) {
			final int index = container.parameterIndex;
			boolean parameterContained = false;
			for (SleuthAnnotatedParameter parameterContainer : annotatedParametersIndices) {
				if (parameterContainer.parameterIndex == index) {
					parameterContained = true;
					break;
				}
			}
			if (!parameterContained) {
				annotatedParametersIndices.add(container);
			}
		}
	}

	private void addAnnotatedArguments(List<SleuthAnnotatedParameter> toBeAdded) {
		for (SleuthAnnotatedParameter container : toBeAdded) {
			// 读取标签值
			String tagValue = resolveTagValue(container.annotation, container.argument);
			// 读取标签键
			String tagKey = resolveTagKey(container);
			// 将标签信息记录到Span上
			span().tag(tagKey, tagValue);
		}
	}

	/**
	 * @return 返回Span自定义器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	private SpanCustomizer span() {
		if (this.spanCustomizer == null) {
			this.spanCustomizer = this.beanFactory.getBean(SpanCustomizer.class);
		}
		return this.spanCustomizer;
	}

	/**
	 * 获取{@link SpanTag}的键
	 *
	 * @param container
	 * @return
	 */
	private String resolveTagKey(SleuthAnnotatedParameter container) {
		return StringUtils.hasText(container.annotation.value()) ? container.annotation.value() : container.annotation.key();
	}

	/**
	 * 使用{@link TagValueResolver}根据参数和注解来解析{@link SpanTag}的值
	 *
	 * @param annotation {@link SpanTag}注解
	 * @param argument 具体的参数值
	 * @return 解析后的值
	 */
	String resolveTagValue(SpanTag annotation, Object argument) {
		String value = null;
		if (annotation.resolver() != NoOpTagValueResolver.class) {
			// 1.尝试使用SpanTag#resolver来解析
			TagValueResolver tagValueResolver = this.beanFactory.getBean(annotation.resolver());
			value = tagValueResolver.resolve(argument);
		}
		else if (StringUtils.hasText(annotation.expression())) {
			// 2.尝试使用TagValueExpressionResolver来解析
			value = this.beanFactory.getBean(TagValueExpressionResolver.class).resolve(annotation.expression(), argument);
		}
		else if (argument != null) {
			// 3.使用toString方法
			value = argument.toString();
		}
		return value == null ? "" : value;
	}

}
