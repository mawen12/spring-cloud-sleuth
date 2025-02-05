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

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanNamer;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.internal.SpanNameUtil;
import org.springframework.util.ReflectionUtils;

/**
 * 在线程运行具有{@link org.springframework.scheduling.annotation.Async}注解的方法时创建一个新的Span的切面
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 * @see Tracer
 */
@Aspect
public class TraceAsyncAspect {

	/**
	 * 跟踪器
	 */
	private final Tracer tracer;

	/**
	 * Span名称生成器
	 */
	private final SpanNamer spanNamer;

	public TraceAsyncAspect(Tracer tracer, SpanNamer spanNamer) {
		this.tracer = tracer;
		this.spanNamer = spanNamer;
	}

	/**
	 * 拦截{@link org.springframework.scheduling.annotation.Async}注解
	 *
	 * @param pjp
	 * @return
	 * @throws Throwable
	 */
	@Around("execution (@org.springframework.scheduling.annotation.Async  * *.*(..))")
	public Object traceBackgroundThread(final ProceedingJoinPoint pjp) throws Throwable {
		// 解析Span名称
		String spanName = name(pjp);
		// 获取当前Span
		Span span = this.tracer.currentSpan();
		if (span == null) {
			// 创建新的Span
			span = this.tracer.nextSpan();
		}
		// 创建可断言的Span
		AssertingSpan assertingSpan = SleuthAsyncSpan.ASYNC_ANNOTATION_SPAN.wrap(span).name(spanName);
		// 启动Span，并设置为当前Span
		try (Tracer.SpanInScope ws = this.tracer.withSpan(assertingSpan.start())) {
			// 添加标签，标签组成为：class=pjp.getTarget().getClass().getSimpleName(), method=pjp.getSignature().getName()
			assertingSpan.tag(SleuthAsyncSpan.Tags.CLASS, pjp.getTarget().getClass().getSimpleName())
					.tag(SleuthAsyncSpan.Tags.METHOD, pjp.getSignature().getName());
			// 执行原始调用
			return pjp.proceed();
		}
		finally {
			// 结束范围
			assertingSpan.end();
		}
	}

	String name(ProceedingJoinPoint pjp) {
		// 以拦截的方法名称作为Span名称，如果不存在则以切面签名作为Span名称
		return this.spanNamer.name(getMethod(pjp, pjp.getTarget()), SpanNameUtil.toLowerHyphen(pjp.getSignature().getName()));
	}

	/**
	 * 获取拦截的方法名称
	 *
	 * @param pjp
	 * @param object
	 * @return
	 */
	private Method getMethod(ProceedingJoinPoint pjp, Object object) {
		MethodSignature signature = (MethodSignature) pjp.getSignature();
		Method method = signature.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

}
