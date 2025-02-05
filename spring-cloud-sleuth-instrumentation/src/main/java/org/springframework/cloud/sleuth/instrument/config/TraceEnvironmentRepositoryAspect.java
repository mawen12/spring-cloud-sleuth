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

package org.springframework.cloud.sleuth.instrument.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

/**
 * 用于拦截{@link org.springframework.cloud.config.server.environment.EnvironmentRepository}方法的切面
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
@Aspect
public class TraceEnvironmentRepositoryAspect {

	private final Tracer tracer;

	public TraceEnvironmentRepositoryAspect(Tracer tracer) {
		this.tracer = tracer;
	}

	@Around("execution (* org.springframework.cloud.config.server.environment.EnvironmentRepository.*(..))")
	public Object traceFindEnvironment(final ProceedingJoinPoint pjp) throws Throwable {
		// @formatter:off
		// 创建新的Span，名称为find，标签有{@code config.environment.class}和{@code config.environment.method}
		AssertingSpan findOneSpan = SleuthConfigSpan.CONFIG_SPAN.wrap(this.tracer.nextSpan())
			.name(SleuthConfigSpan.CONFIG_SPAN.getName())
			.tag(SleuthConfigSpan.Tags.ENVIRONMENT_CLASS, pjp.getTarget().getClass().getName())
			.tag(SleuthConfigSpan.Tags.ENVIRONMENT_METHOD, pjp.getSignature().getName());
		// @formatter:on
		// 开始新的Span，并设置为当前Span，返回Span范围
		try (Tracer.SpanInScope ws = this.tracer.withSpan(findOneSpan.start())) {
			// 执行实际处理
			return pjp.proceed();
		}
		finally {
			// 结束Span
			findOneSpan.end();
		}
	}

}
