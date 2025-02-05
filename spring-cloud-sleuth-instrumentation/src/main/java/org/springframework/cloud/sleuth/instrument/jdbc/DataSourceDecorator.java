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

package org.springframework.cloud.sleuth.instrument.jdbc;

import javax.sql.DataSource;

/**
 * 用于上下文{@link DataSource} bean 的装饰类
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public interface DataSourceDecorator {

	/**
	 * 装饰给定的{@link DataSource}实例，并返回原实例或包装后的实例
	 *
	 * @param beanName name of a bean
	 * @param dataSource bean instance
	 * @return decorated {@link DataSource} or given {@link DataSource} without changes.
	 */
	DataSource decorate(String beanName, DataSource dataSource);

}
