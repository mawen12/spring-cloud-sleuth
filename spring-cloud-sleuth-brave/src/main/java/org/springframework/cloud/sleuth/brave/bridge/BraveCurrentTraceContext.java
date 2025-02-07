/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.sleuth.brave.bridge;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import brave.propagation.ThreadLocalCurrentTraceContext;

import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.TraceContext;

/**
 * 基于Brave的{@link CurrentTraceContext}实现
 */
public class BraveCurrentTraceContext implements CurrentTraceContext {

	/**
	 * 使用线程本地来保存Scope信息
	 */
	final ThreadLocal<Scope> scopes = new ThreadLocal<>();

	/**
	 * Brave的CurrentTraceContext
	 */
	final brave.propagation.CurrentTraceContext delegate;

	public BraveCurrentTraceContext(brave.propagation.CurrentTraceContext delegate) {
		this.delegate = delegate;
	}

	@Override
	public TraceContext context() {
		// 获取Brave的TraceContext
		brave.propagation.TraceContext context = this.delegate.get();
		// 如果不为空，则基于此上下文创建新的上下文，否则返回null
		return context == null ? null : new BraveTraceContext(context);
	}

	@Override
	public CurrentTraceContext.Scope newScope(TraceContext context) {
		if (context == null) {
			// 上下文不存在时，清理线程本地
			clearScopes();
			return Scope.NOOP;
		}
		// 构造恢复范围
		return new RevertingScope(this, new BraveScope(this.delegate.newScope(BraveTraceContext.toBrave(context))));
	}

	@Override
	public CurrentTraceContext.Scope maybeScope(TraceContext context) {
		if (context == null) {
			clearScopes();
			return Scope.NOOP;
		}
		return new RevertingScope(this, new BraveScope(this.delegate.maybeScope(BraveTraceContext.toBrave(context))));
	}

	private void clearScopes() {
		// 获取当前范围
		Scope current = this.scopes.get();
		// 巡检检查并关闭
		while (current != null) {
			current.close();
			current = this.scopes.get();
		}
		if (this.delegate instanceof ThreadLocalCurrentTraceContext) {
			// 清理线程本地
			((ThreadLocalCurrentTraceContext) this.delegate).clear();
		}
	}

	@Override
	public <C> Callable<C> wrap(Callable<C> task) {
		return this.delegate.wrap(task);
	}

	@Override
	public Runnable wrap(Runnable task) {
		return this.delegate.wrap(task);
	}

	@Override
	public Executor wrap(Executor delegate) {
		return this.delegate.executor(delegate);
	}

	@Override
	public ExecutorService wrap(ExecutorService delegate) {
		return this.delegate.executorService(delegate);
	}

	static brave.propagation.CurrentTraceContext toBrave(CurrentTraceContext context) {
		return ((BraveCurrentTraceContext) context).delegate;
	}

	static CurrentTraceContext fromBrave(brave.propagation.CurrentTraceContext context) {
		return new BraveCurrentTraceContext(context);
	}

}

class RevertingScope implements CurrentTraceContext.Scope {

	private final BraveCurrentTraceContext currentTraceContext;

	private final CurrentTraceContext.Scope previous;

	private final CurrentTraceContext.Scope current;

	RevertingScope(BraveCurrentTraceContext currentTraceContext, CurrentTraceContext.Scope current) {
		this.currentTraceContext = currentTraceContext;
		this.previous = this.currentTraceContext.scopes.get();
		this.current = current;
		this.currentTraceContext.scopes.set(this);
	}

	@Override
	public void close() {
		this.current.close();
		this.currentTraceContext.scopes.set(this.previous);
	}

}

/**
 * 基于Brave实现的{@link org.springframework.cloud.sleuth.CurrentTraceContext.Scope}
 */
class BraveScope implements CurrentTraceContext.Scope {

	private final brave.propagation.CurrentTraceContext.Scope delegate;

	BraveScope(brave.propagation.CurrentTraceContext.Scope delegate) {
		this.delegate = delegate;
	}

	@Override
	public void close() {
		this.delegate.close();
	}

}
