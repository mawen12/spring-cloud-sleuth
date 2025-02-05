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

import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoOperator;
import reactor.util.annotation.Nullable;
import reactor.util.context.Context;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.TraceContext;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.cloud.sleuth.instrument.reactor.ReactorSleuth;
import org.springframework.cloud.sleuth.instrument.reactor.TraceContextPropagator;
import org.springframework.util.StringUtils;

/**
 * 用于支持Reactor的方法调用处理器
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
public class ReactorSleuthMethodInvocationProcessor extends AbstractSleuthMethodInvocationProcessor {

	private NonReactorSleuthMethodInvocationProcessor nonReactorSleuthMethodInvocationProcessor;

	@Override
	public Object process(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
		Method method = invocation.getMethod();
		// 检查是否为Reactor返回类型
		if (isReactorReturnType(method.getReturnType())) {
			// Reactor方法采用ReactorSleuthMethodInvocationProcessor处理
			return proceedUnderReactorSpan(invocation, newSpan, continueSpan);
		}
		else {
			// 非Reactor的方法采用NonReactorSleuthMethodInvocationProcessor处理
			return nonReactorSleuthMethodInvocationProcessor().process(invocation, newSpan, continueSpan);
		}
	}

	@SuppressWarnings("unchecked")
	private Object proceedUnderReactorSpan(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable {
		// 获取当前Span
		Span spanPrevious = tracer().currentSpan();
		// in case of @ContinueSpan and no span in tracer we start new span and should
		// close it on completion
		Span span;
		if (newSpan != null || spanPrevious == null) {
			span = null;
		}
		else {
			span = spanPrevious;
		}

		// 读取@ContinueSpan#log信息
		String log = log(continueSpan);
		// 调用目标方法
		Publisher<?> publisher = (Publisher) invocation.proceed();

		if (publisher instanceof Mono) {
			// 处理Mono返回类型
			return new MonoSpan((Mono<Object>) publisher, this, newSpan, span, invocation, log);
		}
		else if (publisher instanceof Flux) {
			// 处理Flux返回类型
			return new FluxSpan((Flux<Object>) publisher, this, newSpan, span, invocation, log);
		}
		else {
			throw new IllegalArgumentException("Unexpected type of publisher: " + publisher.getClass());
		}
	}

	/**
	 * Reactor返回类型为{@link Flux}和{@link Mono}
	 *
	 * @param returnType
	 * @return
	 */
	private boolean isReactorReturnType(Class<?> returnType) {
		return Flux.class.equals(returnType) || Mono.class.equals(returnType);
	}

	private NonReactorSleuthMethodInvocationProcessor nonReactorSleuthMethodInvocationProcessor() {
		if (this.nonReactorSleuthMethodInvocationProcessor == null) {
			this.nonReactorSleuthMethodInvocationProcessor = new NonReactorSleuthMethodInvocationProcessor();
			this.nonReactorSleuthMethodInvocationProcessor.setBeanFactory(this.beanFactory);
		}
		return this.nonReactorSleuthMethodInvocationProcessor;
	}

	private static final class FluxSpan extends FluxOperator<Object, Object> {

		final Span span;

		final MethodInvocation invocation;

		final String log;

		final boolean hasLog;

		final ReactorSleuthMethodInvocationProcessor processor;

		final NewSpan newSpan;

		FluxSpan(Flux<Object> source, ReactorSleuthMethodInvocationProcessor processor, NewSpan newSpan,
				@Nullable Span span, MethodInvocation invocation, String log) {
			super(source);
			this.span = span;
			this.newSpan = newSpan;
			this.invocation = invocation;
			this.log = log;
			this.hasLog = StringUtils.hasText(log);
			this.processor = processor;
		}

		@Override
		public void subscribe(CoreSubscriber<? super Object> actual) {
			Span span;
			Tracer tracer = this.processor.tracer();
			if (this.span == null) {
				// If we aren't continuing a trace from this flow, use nextSpan so that it
				// can consider the "current span" (typically, backed by a thread-local)
				span = SleuthAnnotationSpan.ANNOTATION_NEW_OR_CONTINUE_SPAN.wrap(tracer.nextSpan());
				this.processor.newSpanParser().parse(this.invocation, this.newSpan, span);
				span.start();
			}
			else {
				span = this.span;
			}
			try (CurrentTraceContext.Scope ws = this.processor.currentTraceContext().maybeScope(span.context())) {
				this.source.subscribe(new SpanSubscriber(actual, this.processor, this.invocation, this.span == null,
						span, this.log, this.hasLog));
			}
		}

	}

	/**
	 * 处理返回值为{@link Mono}类型
	 */
	private static final class MonoSpan extends MonoOperator<Object, Object> implements TraceContextPropagator {

		final Span span;

		final MethodInvocation invocation;

		final String log;

		final boolean hasLog;

		final ReactorSleuthMethodInvocationProcessor processor;

		final NewSpan newSpan;

		MonoSpan(Mono<Object> source, ReactorSleuthMethodInvocationProcessor processor, NewSpan newSpan, @Nullable Span span, MethodInvocation invocation, String log) {
			super(source);
			this.processor = processor;
			this.newSpan = newSpan;
			this.span = span;
			this.invocation = invocation;
			this.log = log;
			this.hasLog = StringUtils.hasText(log);
		}

		@Override
		public void subscribe(CoreSubscriber<? super Object> actual) {
			Span span;
			// 获取跟踪器
			Tracer tracer = this.processor.tracer();
			if (this.span == null) {
				// 对于空的Span，创建新的Span
				span = SleuthAnnotationSpan.ANNOTATION_NEW_OR_CONTINUE_SPAN.wrap(tracer.nextSpan());
				// 解析并设置Span的名称
				this.processor.newSpanParser().parse(this.invocation, this.newSpan, span);
				// 开始Span
				span.start();
			}
			else {
				span = this.span;
			}

			try (CurrentTraceContext.Scope ws = this.processor.currentTraceContext().maybeScope(span.context())) {
				// 订阅
				this.source.subscribe(new SpanSubscriber(actual, this.processor, this.invocation, this.span == null, span, this.log, this.hasLog));
			}
		}

		@Override
		public Object scanUnsafe(Attr key) {
			if (key == Attr.RUN_STYLE) {
				return Attr.RunStyle.SYNC;
			}
			return super.scanUnsafe(key);
		}

	}

	private static final class SpanSubscriber implements CoreSubscriber<Object>, Subscription, Scannable {

		final CoreSubscriber<? super Object> actual;

		final boolean isNewSpan;

		final Span span;

		final String log;

		final boolean hasLog;

		final Tracer tracer;

		final ReactorSleuthMethodInvocationProcessor processor;

		final Context context;

		Subscription parent;

		SpanSubscriber(CoreSubscriber<? super Object> actual, ReactorSleuthMethodInvocationProcessor processor,
				MethodInvocation invocation, boolean isNewSpan, Span span, String log, boolean hasLog) {
			this.actual = actual;
			this.isNewSpan = isNewSpan;
			this.span = span;
			this.log = log;
			this.hasLog = hasLog;
			this.processor = processor;
			this.context = ReactorSleuth
					.wrapContext(actual.currentContext().put(Span.class, span).put(TraceContext.class, span.context()));
			this.tracer = processor.tracer();
			processor.before(invocation, this.span, this.log, this.hasLog);
		}

		@Override
		public void request(long n) {
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.parent.request(n);
			}
		}

		@Override
		public void cancel() {
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.parent.cancel();
			}
			finally {
				this.processor.after(this.span, this.isNewSpan, this.log, this.hasLog);
			}
		}

		@Override
		public Context currentContext() {
			return this.context;
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.parent = subscription;
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.actual.onSubscribe(this);
			}
		}

		@Override
		public void onNext(Object o) {
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.actual.onNext(o);
			}
		}

		@Override
		public void onError(Throwable error) {
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.processor.onFailure(this.span, this.log, this.hasLog, error);
				this.actual.onError(error);
			}
			finally {
				this.processor.after(this.span, this.isNewSpan, this.log, this.hasLog);
			}
		}

		@Override
		public void onComplete() {
			try (Tracer.SpanInScope scope = this.tracer.withSpan(this.span)) {
				this.actual.onComplete();
			}
			finally {
				this.processor.after(this.span, this.isNewSpan, this.log, this.hasLog);
			}
		}

		@Override
		public Object scanUnsafe(Attr key) {
			if (key == Attr.ACTUAL) {
				return this.actual;
			}
			if (key == Attr.PARENT) {
				return this.parent;
			}
			return null;
		}

	}

}
