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

package org.springframework.cloud.sleuth.autoconfig.actuate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.cloud.sleuth.exporter.FinishedSpan;
import org.springframework.cloud.sleuth.exporter.SpanReporter;

/**
 * 缓冲最终Span的{@link SpanReporter}
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class BufferingSpanReporter implements SpanReporter {

	/**
	 * 缓存最大容量
	 */
	private final int capacity;

	/**
	 * 预计大小
	 */
	private final AtomicInteger estimatedSize = new AtomicInteger();

	/**
	 * 用于保存最终Span的队列
	 */
	final ConcurrentLinkedQueue<FinishedSpan> spans = new ConcurrentLinkedQueue<>();

	public BufferingSpanReporter(int capacity) {
		this.capacity = capacity;
	}

	/**
	 * 该操作将不会将Span从缓冲区移除。{@link #drainFinishedSpans()}
	 *
	 * @return 当前缓存Span的镜像
	 */
	public List<FinishedSpan> getFinishedSpans() {
		return new ArrayList<>(this.spans);
	}

	/**
	 * 通过从缓冲区拉取Span来返回{@link StartupTimeline}
	 *
	 * <p>该操作会将Span从缓冲区中移除.{@link #getFinishedSpans()}
	 *
	 * @return 缓冲步骤从缓冲区中排出
	 */
	public List<FinishedSpan> drainFinishedSpans() {
		List<FinishedSpan> events = new ArrayList<>();
		Iterator<FinishedSpan> iterator = this.spans.iterator();
		while (iterator.hasNext()) {
			events.add(iterator.next());
			iterator.remove();
		}
		// 重置计数器
		this.estimatedSize.set(0);
		return events;
	}

	@Override
	public void report(FinishedSpan span) {
		if (this.estimatedSize.get() < this.capacity) {
			/**
			 * 如果尚未超过缓冲上限，则放入到缓存中，并增加计数
			 */
			this.estimatedSize.incrementAndGet();
			this.spans.add(span);
		}
		else {
			/**
			 * 移除最早的元素，减少计数，并重新执行放入操作
			 */
			this.spans.poll();
			this.estimatedSize.decrementAndGet();
			report(span);
		}
	}

}
