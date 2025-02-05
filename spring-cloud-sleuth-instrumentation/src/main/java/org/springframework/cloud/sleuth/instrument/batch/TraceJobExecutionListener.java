/*
 * Copyright 2018-2021 the original author or authors.
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

package org.springframework.cloud.sleuth.instrument.batch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;

/**
 * 用于支持跟踪的{@link JobExecutionListener}实现
 */
class TraceJobExecutionListener implements JobExecutionListener {

	/**
	 * 跟踪器
	 */
	private final Tracer tracer;

	private static final Map<JobExecution/* 原始的JobExecution */, SpanAndScope/* 具有范围的Span */> SPANS = new ConcurrentHashMap<>();

	TraceJobExecutionListener(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		// 构造下一个Span，并以jobName作为Span的名称
		Span span = SleuthBatchSpan.BATCH_JOB_SPAN.wrap(this.tracer.nextSpan()).name(jobExecution.getJobInstance().getJobName());
		// 开始Span，并设置为当前Span
		Tracer.SpanInScope spanInScope = this.tracer.withSpan(span.start());
		// 保存到缓存
		SPANS.put(jobExecution, new SpanAndScope(span, spanInScope));
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		// 获取该Job执行对应的Span
		SpanAndScope spanAndScope = SPANS.remove(jobExecution);
		// 获取执行异常
		List<Throwable> throwables = jobExecution.getFailureExceptions();
		// @formatter:off
		// 获取Span，并写入标签：{@code batch.job.name}=jobName, {@code batch.job.instanceId}=instanceId, {@code batch.job.executionId}=executionId
		AssertingSpan span = SleuthBatchSpan.BATCH_JOB_SPAN.wrap(spanAndScope.getSpan())
			.tag(SleuthBatchSpan.JobTags.JOB_NAME, jobExecution.getJobInstance().getJobName())
			.tag(SleuthBatchSpan.JobTags.JOB_INSTANCE_ID, String.valueOf(jobExecution.getJobInstance().getInstanceId()))
			.tag(SleuthBatchSpan.JobTags.JOB_EXECUTION_ID, String.valueOf(jobExecution.getId()));
		// formatter:on
		// 获取Span范围
		Tracer.SpanInScope scope = spanAndScope.getScope();
		if (!throwables.isEmpty()) {
			// 写入异常
			span.error(mergedThrowables(throwables));
		}
		// 结束Span
		span.end();
		// 结束范围
		scope.close();
	}

	private IllegalStateException mergedThrowables(List<Throwable> throwables) {
		return new IllegalStateException(throwables.stream().map(Throwable::toString).collect(Collectors.joining("\n")));
	}

}
