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

import org.springframework.cloud.sleuth.docs.DocumentedSpan;
import org.springframework.cloud.sleuth.docs.EventValue;
import org.springframework.cloud.sleuth.docs.TagKey;

/**
 * 代表Spring Cloud Sleuth JDBC的Span
 */
enum SleuthJdbcSpan implements DocumentedSpan {

	/**
	 * 当执行JDBC查询时创建Span
	 */
	JDBC_QUERY_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "%s";
		}

		/**
		 * @return 只允许存在{@code jdbc.query}和{@code jdbc.row-count}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return QueryTags.values();
		}

		/**
		 * @return 只允许存在{@code jdbc.commit}和{@code jdbc.rollback}的事件
		 */
		@Override
		public EventValue[] getEvents() {
			return QueryEvents.values();
		}

		/**
		 * @return 事件与标签的前缀为jdbc.
		 */
		@Override
		public String prefix() {
			return "jdbc.";
		}
	},

	/**
	 * 当处理JDBC结果集时创建Span
	 */
	JDBC_RESULT_SET_SPAN {
		/**
		 * @return 名称要求：任意字符串
		 */
		@Override
		public String getName() {
			return "result-set";
		}

		/**
		 * @return 只允许存在{@code jdbc.query}和{@code jdbc.row-count}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return QueryTags.values();
		}

		/**
		 * @return 只允许存在{@code jdbc.commit}和{@code jdbc.rollback}的事件
		 */
		@Override
		public EventValue[] getEvents() {
			return QueryEvents.values();
		}

		/**
		 * @return 事件与标签的前缀为jdbc.
		 */
		@Override
		public String prefix() {
			return "jdbc.";
		}
	},

	/**
	 * 在发生JDBC连接时创建Span
	 */
	JDBC_CONNECTION_SPAN {
		/**
		 * @return 名称要求：connection
		 */
		@Override
		public String getName() {
			return "connection";
		}

		/**
		 * @return 只允许存在{@code jdbc.datasource.driver}和{@code jdbc.datasource.pool}的键
		 */
		@Override
		public TagKey[] getTagKeys() {
			return ConnectionTags.values();
		}
		/**
		 * @return 事件与标签的前缀为jdbc.
		 */
		@Override
		public String prefix() {
			return "jdbc.";
		}
	};

	enum ConnectionTags implements TagKey {

		/**
		 * JDBC数据源驱动名称
		 */
		DATASOURCE_DRIVER {
			@Override
			public String getKey() {
				return "jdbc.datasource.driver";
			}
		},

		/**
		 * JDBC连接次大小
		 */
		DATASOURCE_POOL {
			@Override
			public String getKey() {
				return "jdbc.datasource.pool";
			}
		},

	}

	enum QueryTags implements TagKey {

		/**
		 * SQL查询值
		 */
		QUERY {
			@Override
			public String getKey() {
				return "jdbc.query";
			}
		},

		/**
		 * SQL返回结果数
		 */
		ROW_COUNT {
			@Override
			public String getKey() {
				return "jdbc.row-count";
			}
		}

	}

	enum QueryEvents implements EventValue {

		/**
		 * 事务提交时事件
		 */
		COMMIT {
			@Override
			public String getValue() {
				return "jdbc.commit";
			}
		},

		/**
		 * 事务回滚时事件
		 */
		ROLLBACK {
			@Override
			public String getValue() {
				return "jdbc.rollback";
			}
		}

	}

}
