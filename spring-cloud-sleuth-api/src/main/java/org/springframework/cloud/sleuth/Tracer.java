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

package org.springframework.cloud.sleuth;

import java.io.Closeable;

import org.springframework.cloud.sleuth.propagation.Propagator;
import org.springframework.lang.Nullable;

/**
 * 此API深受Brave影响，其部分文档直接取自Brave.
 *
 * <p>使用{@link Tracer}，可以创建一个根{@link Span}来捕获请求的关键路径。
 * 可以创建子{@link Span}来分配与传出请求相关的延迟。
 *
 * <p>当跟踪单线程代码时，只需在范围内{@link Span}运行该代码：
 * <pre>{@code
 *	// Start a new trace or a span within an existing trace representing an operation
 *	Scoped span = tracer.startScopedSpan("encode");
 *	try {
 *	   // The span is in "scope" so that downstream code such as loggers can see trace IDs
 *	   return encoder.encode();
 *	} catch (RuntimeException | Error e) {
 *	   // Unless you handle exceptions, you might not known the operation failed!
 *	   span.error(e);
 *	   throw e;
 *	} finally {
 *	   // note the scope is independent of the span. Always finish a span.
 * 	   span.end();
 * 	}
 * }</pre>
 *
 * <p>如果需要更多功能或更精细的控制时，请使用{@link Span} 类型：
 * <pre>{@code
 * 	// Start a new trace or a span within an existing trace representing an operation
 * 	Span span = tracer.nextSpan().name("encode").start();
 * 	// Put the span in "scope" so that downstream code such as loggers can see trace IDs
 * 	try (SpanInScope ws = tracer.withSpanInScope(span)){
 * 	    return encoder.encode();
 * 	}
 * 	catch (RuntimeException | Error e) {
 * 		// Unless you handle exceptions, you might not known the operation failed!
 * 	    span.error(e);
 * 	    throw e;
 * 	} finally {
 * 	    // note the scope is independent of the span. Always finish a span.
 * 	    span.end();
 * 	}
 * }</pre>
 *
 * 以上两个示例报告的完成跨度完全相同！
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 3.0.0
 * @see Span
 * @see ScopedSpan
 * @see Propagator
 */
public interface Tracer extends BaggageManager {

	/**
	 * 根据范围内的当前{@link Span}创建一个新的{@link Span}，如果此处没有任何{@link Span}，则创建一个新的{@link Tracer}
	 *
	 * @return 创建一个子级Span，如果不存在则创建一个新的Tracer.
	 */
	Span nextSpan();

	/**
	 * 以指定{@link Span}作为父级创建一个新的{@link Span}, 如果父级为空，则行为类似于{@link #nextSpan()}
	 *
	 * @param parent 父级Span
	 * @return 使用给定Span创建一个子级Span，如果不存在则创建一个新的Tracer.
	 */
	Span nextSpan(@Nullable Span parent);

	/**
	 * 将给定{@link Span}设置为当前{@link Span}，并返回在关闭时退出该范围的对象。
	 * 对{@link #currentSpan()} 和 {@link #currentSpanCustomizer()} 的调用将影响该{@link Span}，
	 * 直到返回值关闭。
	 *
	 * <p>使用此方法最方便的方式是通过try-with-resources。
	 *
	 * <p>当跟踪进程内命令时，最好使用{@link #startScopedSpan(String)}，默认情况下会指定范围。
	 *
	 * <p>虽然下游代码可能影响{@link Span}，但调用该方法以及对结果调用关闭方法不会对输入产生影响。
	 * 例如：对结果调用关闭不会结束范围。不仅调用关闭是安全的，而且必须调用关闭来结束范围，否则可能会
	 * 泄漏与范围相关的资源。
	 *
	 * @param span span to place into scope or null to clear the scope
	 * @return scope with span in it
	 */
	Tracer.SpanInScope withSpan(@Nullable Span span);

	/**
	 * 如果{@link #currentSpan()}存在则返回{@link Span}，否则创建一个新的{@link Tracer}。
	 * 返回结果是当前{@link Span}直到调用{@link ScopedSpan#end()}。
	 *
	 * <p>代码示例:
	 * <pre>{@code
	 * 	ScopedSpan span = tracer.startScopedSpan("encode");
	 * 	try {
	 * 	    return encoder.encode();
	 * 	} catch (RuntimeException | Error e) {
	 * 		// Unless you handle exceptions, you might not known the operation failed!
	 * 	    span.error(e);
	 * 	    throw e;
	 * 	} finally {
	 * 	    // note the scope is independent of the span. Always finish a span.
	 * 	    span.end();
	 * 	}
	 * }</pre>
	 *
	 * @param name of the span in scope
	 * @return span in scope
	 */
	ScopedSpan startScopedSpan(String name);

	/**
	 * 用于在某些场景下处理{@link Propagator#extract(Object, Propagator.Getter)}时，
	 * 想要创建一个尚未启动的{@link Span}，但它具有很强的可配置性(当span已经启动时，某些选项无法配置)。
	 * 我们可以使用构建器来实现这一点。
	 *
	 * @return a span builder
	 */
	Span.Builder spanBuilder();

	/**
	 * {@link TraceContext}的构建器
	 *
	 * @return a trace context builder
	 */
	TraceContext.Builder traceContextBuilder();

	/**
	 * 返回{@link CurrentTraceContext}，可以为null，这样就不会破坏向后兼容性。
	 *
	 * @return current trace context
	 */
	@Nullable
	default CurrentTraceContext currentTraceContext() {
		return null;
	}

	/**
	 * 允许自定义范围的当前的{@link Span}.
	 *
	 * @return current span customizer
	 */
	@Nullable
	SpanCustomizer currentSpanCustomizer();

	/**
	 * 检索范围内当前的{@link Span}，如果不存在者返回null。
	 *
	 * @return current span in scope
	 */
	@Nullable
	Span currentSpan();

	/**
	 * {@link Span}的范围，需要调用{@link #close()}来释放资源，例如清理MDC
	 */
	interface SpanInScope extends Closeable {

		/**
		 * Noop instance.
		 */
		SpanInScope NOOP = () -> {

		};

		@Override
		void close();

	}

}
