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

import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Tracer;

/**
 * 用于向{@link JobBuilderFactory}添加{@link TraceJobExecutionListener}的工厂
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public class TraceJobBuilderFactory extends JobBuilderFactory {

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	/**
	 * 原始类
	 */
	private final JobBuilderFactory delegate;

	/**
	 * 跟踪器
	 */
	private Tracer tracer;

	public TraceJobBuilderFactory(BeanFactory beanFactory, JobBuilderFactory delegate) {
		super(null);
		this.beanFactory = beanFactory;
		this.delegate = delegate;
	}

	/**
	 * 返回指定的{@link JobBuilder}，并添加{@link TraceJobExecutionListener}
	 *
	 * @param name
	 * @return
	 */
	@Override
	public JobBuilder get(String name) {
		return this.delegate.get(name).listener(new TraceJobExecutionListener(tracer()));
	}

	/**
	 * @return 返回跟踪器，如果不存在则从{@link BeanFactory#getBean(Class)}获取
	 */
	private Tracer tracer() {
		if (this.tracer == null) {
			this.tracer = this.beanFactory.getBean(Tracer.class);
		}
		return this.tracer;
	}

}
