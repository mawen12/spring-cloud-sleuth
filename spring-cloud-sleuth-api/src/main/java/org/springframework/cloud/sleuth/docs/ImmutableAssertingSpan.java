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

package org.springframework.cloud.sleuth.docs;

import java.util.Objects;

import org.springframework.cloud.sleuth.Span;

import static java.util.Objects.requireNonNull;

/**
 * 基于不可变的{@link AssertingSpan}实现
 */
class ImmutableAssertingSpan implements AssertingSpan {

	/**
	 * 可文档化的Span
	 */
	private final DocumentedSpan documentedSpan;

	/**
	 * 原始Span
	 */
	private final Span delegate;

	/**
	 * 原始Span启动状态标识
	 */
	boolean isStarted;

	ImmutableAssertingSpan(DocumentedSpan documentedSpan, Span delegate) {
		requireNonNull(documentedSpan);
		requireNonNull(delegate);
		this.documentedSpan = documentedSpan;
		this.delegate = delegate;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ImmutableAssertingSpan that = (ImmutableAssertingSpan) o;
		return Objects.equals(documentedSpan, that.documentedSpan) && Objects.equals(delegate, that.delegate);
	}

	@Override
	public String toString() {
		return this.delegate.toString();
	}

	@Override
	public int hashCode() {
		return Objects.hash(documentedSpan, delegate);
	}

	@Override
	public DocumentedSpan getDocumentedSpan() {
		return this.documentedSpan;
	}

	@Override
	public Span getDelegate() {
		return this.delegate;
	}

	@Override
	public AssertingSpan start() {
		// 更新启动标识
		this.isStarted = true;
		// 使用父类方法进行启动
		return AssertingSpan.super.start();
	}

	@Override
	public boolean isStarted() {
		return this.isStarted;
	}

}
