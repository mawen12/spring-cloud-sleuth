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

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import net.ttddyy.dsproxy.listener.logging.CommonsLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * 用于数据源代理的属性
 *
 * @author Arthur Gavlyukovskiy
 * @since 3.1.0
 */
public class DataSourceProxyProperties {

	/**
	 * 用于记录查询的日志
	 */
	private DataSourceProxyLogging logging = DataSourceProxyLogging.SLF4J;

	/**
	 * 查询配置
	 */
	private Query query = new Query();

	/**
	 * 慢查询配置
	 */
	private SlowQuery slowQuery = new SlowQuery();

	/**
	 * 是否使用多行输出来记录查询，默认是
	 *
	 * @see ProxyDataSourceBuilder#multiline()
	 */
	private boolean multiline = true;

	/**
	 * 是否使用json输出来记录查询，默认否
	 *
	 * @see ProxyDataSourceBuilder#asJson()
	 */
	private boolean jsonFormat = false;

	public DataSourceProxyLogging getLogging() {
		return logging;
	}

	public void setLogging(DataSourceProxyLogging logging) {
		this.logging = logging;
	}

	public Query getQuery() {
		return query;
	}

	public void setQuery(Query query) {
		this.query = query;
	}

	public SlowQuery getSlowQuery() {
		return slowQuery;
	}

	public void setSlowQuery(SlowQuery slowQuery) {
		this.slowQuery = slowQuery;
	}

	public boolean isMultiline() {
		return multiline;
	}

	public void setMultiline(boolean multiline) {
		this.multiline = multiline;
	}

	public boolean isJsonFormat() {
		return jsonFormat;
	}

	public void setJsonFormat(boolean jsonFormat) {
		this.jsonFormat = jsonFormat;
	}

	/**
	 * 用于配置查询日志监听器的配置
	 *
	 * @see ProxyDataSourceBuilder#logQueryToSysOut()
	 * @see ProxyDataSourceBuilder#logQueryBySlf4j(SLF4JLogLevel, String)
	 * @see ProxyDataSourceBuilder#logQueryByCommons(CommonsLogLevel, String)
	 * @see ProxyDataSourceBuilder#logQueryByJUL(Level, String)
	 */
	public static class Query {

		/**
		 * 是否将所有查询记录到日志
		 */
		private boolean enableLogging = false;

		/**
		 * 对应查询的日志记录器的名称
		 */
		private String loggerName;

		/**
		 * 查询记录器的级别，默认为DEBUG
		 */
		private String logLevel = "DEBUG";

		public boolean isEnableLogging() {
			return enableLogging;
		}

		public void setEnableLogging(boolean enableLogging) {
			this.enableLogging = enableLogging;
		}

		public String getLoggerName() {
			return loggerName;
		}

		public void setLoggerName(String loggerName) {
			this.loggerName = loggerName;
		}

		public String getLogLevel() {
			return logLevel;
		}

		public void setLogLevel(String logLevel) {
			this.logLevel = logLevel;
		}

	}

	/**
	 * 用于配置慢查询日志监听器的配置
	 *
	 * @see ProxyDataSourceBuilder#logSlowQueryToSysOut(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryBySlf4j(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryByCommons(long, TimeUnit)
	 * @see ProxyDataSourceBuilder#logSlowQueryByJUL(long, TimeUnit)
	 */
	public static class SlowQuery {

		/**
		 * 是否将慢查询记录到日志中，默认否
		 */
		private boolean enableLogging = false;

		/**
		 * 慢查询记录器的名称
		 */
		private String loggerName;

		/**
		 * 满查询日志的级别
		 */
		private String logLevel = "WARN";

		/**
		 * 认为查询很慢的秒数，默认为5分钟
		 */
		private long threshold = 300;

		boolean isEnableLogging() {
			return enableLogging;
		}

		public void setEnableLogging(boolean enableLogging) {
			this.enableLogging = enableLogging;
		}

		public String getLoggerName() {
			return loggerName;
		}

		public void setLoggerName(String loggerName) {
			this.loggerName = loggerName;
		}

		public String getLogLevel() {
			return logLevel;
		}

		public void setLogLevel(String logLevel) {
			this.logLevel = logLevel;
		}

		public long getThreshold() {
			return threshold;
		}

		public void setThreshold(long threshold) {
			this.threshold = threshold;
		}

	}

	/**
	 * Query logging listener is the most used listener that logs executing query with
	 * actual parameters to. You can pick one of the following proxy logging mechanisms.
	 *
	 * 查询日志监听器是最常用的监听器，它记录带有实际参数的执行查询。
	 * 可以选择以下代理日志记录机制之一。
	 */
	public enum DataSourceProxyLogging {

		/**
		 * 使用{@link System#out}作为输出
		 */
		SYSOUT,

		/**
		 * 使用{@code SLF4J}作为输出
		 */
		SLF4J,

		/**
		 * 使用{@code Commons}作为输出
		 */
		COMMONS,

		/**
		 * 使用{@code Java Util Logging}作为输出
		 */
		JUL

	}

}
