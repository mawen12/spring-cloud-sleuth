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

package org.springframework.cloud.sleuth.instrument.batch;

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.TagKey;

/**
 * 代表Spring Cloud Sleuth批次的Span
 *
 * <p>支持Spring Batch
 */
enum SleuthBatchSpan implements DocumentedSpan {

	/**
	 * 创建一个围绕Job执行的Span
	 */
	BATCH_JOB_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

		/**
		 * @return 只允许存在名为 {@code batch.job.name}, {@code batch.job.instanceId} 和 {@code batch.job.executionId}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return JobTags.values();
		}

	},

	/**
	 * Span created around a Job execution.
	 * 创建一个围绕Job执行步骤的Span
	 */
	BATCH_STEP_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

		/**
		 * @return 只允许存在名为 {@code batch.step.name}，{@code batch.step.executionId}，{@code batch.step.type} 和 {@code batch.job.executionId}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return StepTags.values();
		}

	};

	enum JobTags implements TagKey {

		/**
		 * Spring Batch Job名称
		 */
		JOB_NAME {
			@Override
			public String getKey() {
				return "batch.job.name";
			}
		},

		/**
		 * Spring Batch Job实例ID
		 */
		JOB_INSTANCE_ID {
			@Override
			public String getKey() {
				return "batch.job.instanceId";
			}
		},

		/**
		 * Spring Batch Job执行ID
		 */
		JOB_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.job.executionId";
			}
		},

	}

	enum StepTags implements TagKey {

		/**
		 * Spring Batch Job Step 名称
		 */
		STEP_NAME {
			@Override
			public String getKey() {
				return "batch.step.name";
			}
		},

		/**
		 * Spring Batch Job Step 执行ID
		 */
		STEP_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.step.executionId";
			}
		},

		/**
		 * Spring Batch Job Step 类型
		 */
		STEP_TYPE {
			@Override
			public String getKey() {
				return "batch.step.type";
			}
		},

		/**
		 * Spring Batch Job 执行ID
		 */
		JOB_EXECUTION_ID {
			@Override
			public String getKey() {
				return "batch.job.executionId";
			}
		},

	}

}
