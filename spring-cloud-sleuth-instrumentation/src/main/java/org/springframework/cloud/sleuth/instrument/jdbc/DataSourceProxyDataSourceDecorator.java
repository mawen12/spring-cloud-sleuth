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

import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

import org.springframework.core.Ordered;

/**
 * 用于{@link ProxyDataSource}的装饰器，支持{@link Ordered}
 *
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class DataSourceProxyDataSourceDecorator implements DataSourceDecorator, Ordered {

	/**
	 * 数据源代理构建器自定义器
	 */
	private final DataSourceProxyBuilderCustomizer dataSourceProxyBuilderCustomizer;

	/**
	 * 数据源名称解析器
	 */
	private final DataSourceNameResolver dataSourceNameResolver;

	public DataSourceProxyDataSourceDecorator(DataSourceProxyBuilderCustomizer dataSourceProxyBuilderCustomizer, DataSourceNameResolver dataSourceNameResolver) {
		this.dataSourceProxyBuilderCustomizer = dataSourceProxyBuilderCustomizer;
		this.dataSourceNameResolver = dataSourceNameResolver;
	}

	@Override
	public DataSource decorate(String beanName, DataSource dataSource) {
		// 创建代理数据源构建器
		ProxyDataSourceBuilder proxyDataSourceBuilder = ProxyDataSourceBuilder.create();
		// 自定义代理数据源构建器
		proxyDataSourceBuilder = this.dataSourceProxyBuilderCustomizer.customize(proxyDataSourceBuilder);
		// 从数据源中获取数据源名称
		String dataSourceName = this.dataSourceNameResolver.resolveDataSourceName(dataSource);
		// 将DataSource包装为ProxyDataSource
		return proxyDataSourceBuilder.dataSource(dataSource).name(dataSourceName).build();
	}

	@Override
	public int getOrder() {
		return 20;
	}

}
