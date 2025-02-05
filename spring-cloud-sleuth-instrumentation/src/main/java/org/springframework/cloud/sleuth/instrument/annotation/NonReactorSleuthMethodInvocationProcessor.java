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

import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.util.StringUtils;

/**
 * 用于支持非Reactor的方法调用处理器
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
public class NonReactorSleuthMethodInvocationProcessor extends AbstractSleuthMethodInvocationProcessor {

	@Override
	public Object process(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
		return proceedUnderSynchronousSpan(invocation, newSpan, continueSpan);
	}

	private Object proceedUnderSynchronousSpan(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
		// 获取当前Span
		Span span = tracer().currentSpan();
		// in case of @ContinueSpan and no span in tracer we start new span and should
		// close it on completion
		boolean startNewSpan = newSpan != null || span == null;
		if (startNewSpan) {
			// 存在@NewSpan或当前span为空，创建新的Span
			span = SleuthAnnotationSpan.ANNOTATION_NEW_OR_CONTINUE_SPAN.wrap(tracer().nextSpan());
			// 解析@NewSpan注解，并设置Span名称
			newSpanParser().parse(invocation, newSpan, span);
			// 开启Span
			span.start();
		}
		// 读取@ContinueSpan#log信息
		String log = log(continueSpan);
		// 检查是否需要记录日志
		boolean hasLog = StringUtils.hasText(log);
		// 设置为当前Span，并返回范围
		try (Tracer.SpanInScope scope = tracer().withSpan(span)) {
			// 触发before事件
			before(invocation, span, log, hasLog);
			// 执行原始调用
			return invocation.proceed();
		}
		catch (Exception ex) {
			// 触发afterFailure事件
			onFailure(span, log, hasLog, ex);
			throw ex;
		}
		finally {
			// 触发after事件
			after(span, startNewSpan, log, hasLog);
		}
	}

}
