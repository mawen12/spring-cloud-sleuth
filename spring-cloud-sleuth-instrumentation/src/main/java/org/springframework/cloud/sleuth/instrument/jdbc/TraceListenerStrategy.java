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

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.CommonDataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanAndScope;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.docs.AssertingSpan;
import org.springframework.cloud.sleuth.docs.AssertingSpanBuilder;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * 跟踪监听器策略会尽最大努力跟踪所有打开的JDBC资源（包含Span和Scope），主要有两个原因：
 * <ul>
 *     <li>
 *         JDBC允许不关闭子资源，在这种情况下，关闭父资源将关闭所有内容。{@link Connection#close()}将关闭所有底层{@link Statement}，
 *         并且{@link Statement#close()}将关闭所有底层{@link ResultSet}。理想情况下，这不应该发生，但实际上某些应用程序（和某些框架）依赖于此机制
 *     </li>
 *     <li>
 *         虽然大多数JDBC驱动程序不支持并发，但可能会在同一个线程中同时打开多个连接。JDBC将这些连接视为完全独立的资源，我们不能依赖关闭这些连接的顺序，
 *     </li>
 * </ul>
 *
 * <p>只要资源在打开的同一线程中关闭，跟踪就会涵盖此类情况。
 *
 * @param <CON> connection type
 * @param <STMT> statement
 * @param <RS> result set
 * @author Arthur Gavlyukovskiy
 * @see <a href="https://github.com/openzipkin/brave/blob/v5.6.4/instrumentation/p6spy/src/main/java/brave/p6spy/TracingJdbcEventListener.java">TracingJdbcEventListener</a>
 * @see <a href="https://github.com/gavlyukovskiy/spring-boot-data-source-decorator/blob/master/datasource-decorator-spring-boot-autoconfigure/src/main/java/com/github/gavlyukovskiy/cloud/sleuth/TracingListenerStrategy.java">TracingListenerStrategy</a>
 */
class TraceListenerStrategy<CON, STMT, RS> {

	private static final Log log = LogFactory.getLog(TraceListenerStrategy.class);

	// Captures all the characters between = and either the next & or the end of the
	// string.
	private static final Pattern URL_SERVICE_NAME_FINDER = Pattern.compile("sleuthServiceName=(.*?)(?:&|$)");

	private static final SpanNameProvider SPAN_NAME_PROVIDER = new SpanNameProvider();

	private final Map<CON, ConnectionInfo> openConnections = new ConcurrentHashMap<>();

	private final ThreadLocal<ConnectionInfo> currentConnection = new ThreadLocal<>();

	/**
	 * 跟踪类型
	 */
	private final List<TraceType> traceTypes;

	private final List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers;

	/**
	 * Bean工厂
	 */
	private BeanFactory beanFactory;

	/**
	 * 跟踪器
	 */
	private Tracer tracer;

	TraceListenerStrategy(Tracer tracer, List<TraceType> traceTypes, List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers) {
		this.traceTypes = traceTypes;
		this.customizers = customizers;
		this.tracer = tracer;
	}

	TraceListenerStrategy(BeanFactory beanFactory, List<TraceType> traceTypes, List<TraceListenerStrategySpanCustomizer<? super CommonDataSource>> customizers) {
		this.traceTypes = traceTypes;
		this.customizers = customizers;
		this.beanFactory = beanFactory;
	}

	/**
	 * 获取连接之前执行
	 *
	 * @param connectionKey
	 * @param dataSource
	 * @param dataSourceName
	 */
	void beforeGetConnection(CON connectionKey, @Nullable CommonDataSource dataSource, String dataSourceName) {
		if (log.isTraceEnabled()) {
			log.trace("Before get connection key [" + connectionKey + "] - current span is [" + getTracer().currentSpan() + "]");
		}
		SpanAndScope spanAndScope = null;
		// 检查是否需要跟踪Connection
		if (this.traceTypes.contains(TraceType.CONNECTION)) {
			// 创建SpanBuilder，其名称为connection
			AssertingSpanBuilder connectionSpanBuilder = AssertingSpanBuilder
					.of(SleuthJdbcSpan.JDBC_CONNECTION_SPAN, getTracer().spanBuilder())
					.name(SleuthJdbcSpan.JDBC_CONNECTION_SPAN.getName());
			// 设置远程服务名称为数据源名称
			connectionSpanBuilder.remoteServiceName(dataSourceName);
			// 设置Span类型为Client
			connectionSpanBuilder.kind(Span.Kind.CLIENT);
			// 对满足条件的数据源进行定制化处理，设置其Span标签信息，有数据源驱动、连接池名称
			this.customizers.stream().filter(customizer -> customizer.isApplicable(dataSource))
					.forEach(customizer -> customizer.customizeConnectionSpan(dataSource, connectionSpanBuilder));
			// 启动Span
			Span connectionSpan = connectionSpanBuilder.start();
			// 开启范围
			Tracer.SpanInScope scope = isCurrent(null) ? getTracer().withSpan(connectionSpan) : null;
			// 构建Span和Scope
			spanAndScope = new SpanAndScope(connectionSpan, scope);
			if (log.isTraceEnabled()) {
				log.trace("Started client span before connection [" + connectionSpan + "] - current span is ["
						+ getTracer().currentSpan() + "]");
			}
		}
		// 创建连接信息
		ConnectionInfo connectionInfo = new ConnectionInfo(spanAndScope);
		//
		connectionInfo.remoteServiceName = dataSourceName;
		this.openConnections.put(connectionKey, connectionInfo);
		if (isCurrent(null)) {
			this.currentConnection.set(connectionInfo);
		}
	}

	void afterGetConnection(CON connectionKey, @Nullable Connection connection, String dataSourceName,
			@Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After get connection [" + connectionKey + "]. Current span is [" + getTracer().currentSpan()
					+ "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connection != null) {
			parseAndSetServerIpAndPort(connectionInfo, connection, dataSourceName);
			if (connectionSpan != null) {
				connectionSpan.getSpan().remoteServiceName(connectionInfo.remoteServiceName);
				if (connectionInfo.url != null) {
					connectionSpan.getSpan().remoteIpAndPort(connectionInfo.url.getHost(),
							connectionInfo.url.getPort());
				}
			}
		}
		else if (t != null) {
			this.openConnections.remove(connectionKey);
			if (isCurrent(connectionInfo)) {
				this.currentConnection.remove();
			}
			if (connectionSpan != null) {
				if (log.isTraceEnabled()) {
					log.trace("Closing client span due to exception [" + connectionSpan.getSpan()
							+ "] - current span is [" + getTracer().currentSpan() + "]");
				}
				connectionSpan.getSpan().error(t);
				connectionSpan.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + getTracer().currentSpan() + "]");
				}
			}
		}
	}

	/**
	 * Returns true if connection belong to the one, that is currently in scope.
	 */
	private boolean isCurrent(@Nullable ConnectionInfo connectionInfo) {
		return this.currentConnection.get() == connectionInfo;
	}

	void beforeQuery(CON connectionKey, STMT statementKey) {
		if (log.isTraceEnabled()) {
			log.trace("Before query - connection [" + connectionKey + "] and current span [" + getTracer().currentSpan()
					+ "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("Connection may be closed after statement preparation, but before statement execution");
			}
			return;
		}
		SpanAndScope spanAndScope = null;
		if (traceTypes.contains(TraceType.QUERY)) {
			Span.Builder statementSpanBuilder = AssertingSpanBuilder
					.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, getTracer().spanBuilder())
					.name(String.format(SleuthJdbcSpan.JDBC_QUERY_SPAN.getName(), "query"));
			statementSpanBuilder.remoteServiceName(connectionInfo.remoteServiceName);
			if (connectionInfo.url != null) {
				statementSpanBuilder.remoteIpAndPort(connectionInfo.url.getHost(), connectionInfo.url.getPort());
			}
			statementSpanBuilder.kind(Span.Kind.CLIENT);
			Span statementSpan = statementSpanBuilder.start();
			Tracer.SpanInScope scope = isCurrent(connectionInfo) ? getTracer().withSpan(statementSpan) : null;
			spanAndScope = new SpanAndScope(statementSpan, scope);
			if (log.isTraceEnabled()) {
				log.trace("Started client span before query [" + statementSpan + "] - current span is ["
						+ getTracer().currentSpan() + "]");
			}
		}
		StatementInfo statementInfo = new StatementInfo(spanAndScope);
		connectionInfo.nestedStatements.put(statementKey, statementInfo);
	}

	void addQueryRowCount(CON connectionKey, STMT statementKey, int rowCount) {
		if (log.isTraceEnabled()) {
			log.trace("Add query row count for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("Connection is already closed");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		SpanAndScope statementSpan = statementInfo.span;
		if (statementSpan != null) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.ROW_COUNT, String.valueOf(rowCount));
		}
	}

	void afterQuery(CON connectionKey, STMT statementKey, String sql, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After query for connection key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace(
						"Connection may be closed after statement preparation, but before statement execution. Current span is ["
								+ getTracer().currentSpan() + "]");
			}
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		SpanAndScope statementSpan = statementInfo.span;
		if (statementSpan != null) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, statementSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.QUERY, sql).name(SPAN_NAME_PROVIDER.getSpanNameFor(sql));
			if (t != null) {
				statementSpan.getSpan().error(t);
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing statement span [" + statementSpan + "] - current span is ["
						+ getTracer().currentSpan() + "]");
			}
			statementSpan.close();
			if (log.isTraceEnabled()) {
				log.trace("Current span [" + getTracer().currentSpan() + "]");
			}
		}
	}

	void beforeResultSetNext(CON connectionKey, STMT statementKey, RS resultSetKey) {
		if (log.isTraceEnabled()) {
			log.trace("Before result set next");
		}
		if (!traceTypes.contains(TraceType.FETCH)) {
			return;
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			if (log.isTraceEnabled()) {
				log.trace("No connection info, skipping");
			}
			return;
		}
		if (connectionInfo.nestedResultSetSpans.containsKey(resultSetKey)) {
			if (log.isTraceEnabled()) {
				log.trace("ResultSet span is already created");
			}
			return;
		}
		AssertingSpanBuilder resultSetSpanBuilder = AssertingSpanBuilder
				.of(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN, getTracer().spanBuilder())
				.name(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN.getName());
		resultSetSpanBuilder.kind(Span.Kind.CLIENT);
		resultSetSpanBuilder.remoteServiceName(connectionInfo.remoteServiceName);
		if (connectionInfo.url != null) {
			resultSetSpanBuilder.remoteIpAndPort(connectionInfo.url.getHost(), connectionInfo.url.getPort());
		}
		Span resultSetSpan = resultSetSpanBuilder.start();
		Tracer.SpanInScope scope = isCurrent(connectionInfo) ? getTracer().withSpan(resultSetSpan) : null;
		SpanAndScope spanAndScope = new SpanAndScope(resultSetSpan, scope);
		if (log.isTraceEnabled()) {
			log.trace("Started client result set span [" + resultSetSpan + "] - current span is ["
					+ getTracer().currentSpan() + "]");
		}
		connectionInfo.nestedResultSetSpans.put(resultSetKey, spanAndScope);
		StatementInfo statementInfo = connectionInfo.nestedStatements.get(statementKey);
		// StatementInfo may be null when Statement is proxied and instance returned from
		// ResultSet is different from instance returned in query method
		// in this case if Statement is closed before ResultSet span won't be finished
		// immediately, but when Connection is closed
		if (statementInfo != null) {
			statementInfo.nestedResultSetSpans.put(resultSetKey, spanAndScope);
		}
	}

	void afterStatementClose(CON connectionKey, STMT statementKey) {
		if (log.isTraceEnabled()) {
			log.trace("After statement close");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before Statement
		if (connectionInfo == null) {
			return;
		}
		StatementInfo statementInfo = connectionInfo.nestedStatements.remove(statementKey);
		if (statementInfo != null) {
			statementInfo.nestedResultSetSpans.forEach((resultSetKey, span) -> {
				connectionInfo.nestedResultSetSpans.remove(resultSetKey);
				if (log.isTraceEnabled()) {
					log.trace("Closing span after statement close [" + span.getSpan() + "] - current span is ["
							+ getTracer().currentSpan() + "]");
				}
				span.close();
				if (log.isTraceEnabled()) {
					log.trace("Current span [" + getTracer().currentSpan() + "]");
				}
			});
			statementInfo.nestedResultSetSpans.clear();
		}
	}

	void afterResultSetClose(CON connectionKey, RS resultSetKey, int rowCount, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After result set close");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		// ConnectionInfo may be null if Connection was closed before ResultSet
		if (connectionInfo == null) {
			return;
		}
		SpanAndScope resultSetSpan = connectionInfo.nestedResultSetSpans.remove(resultSetKey);
		// ResultSet span may be null if Statement or ResultSet were already closed
		if (resultSetSpan == null) {
			return;
		}
		if (rowCount != -1) {
			AssertingSpan.of(SleuthJdbcSpan.JDBC_RESULT_SET_SPAN, resultSetSpan.getSpan())
					.tag(SleuthJdbcSpan.QueryTags.ROW_COUNT, String.valueOf(rowCount));
		}
		if (t != null) {
			resultSetSpan.getSpan().error(t);
		}
		if (log.isTraceEnabled()) {
			log.trace("Closing client result set span [" + resultSetSpan + "] - current span is ["
					+ getTracer().currentSpan() + "]");
		}
		resultSetSpan.close();
		if (log.isTraceEnabled()) {
			log.trace("Current span [" + getTracer().currentSpan() + "]");
		}
	}

	void afterCommit(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After commit");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.COMMIT);
		}
	}

	void afterRollback(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After rollback");
		}
		ConnectionInfo connectionInfo = this.openConnections.get(connectionKey);
		if (connectionInfo == null) {
			// Connection is already closed
			return;
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			else {
				connectionSpan.getSpan().error(new JdbcException("Transaction rolled back"));
			}
			AssertingSpan.of(SleuthJdbcSpan.JDBC_QUERY_SPAN, connectionSpan.getSpan())
					.event(SleuthJdbcSpan.QueryEvents.ROLLBACK);
		}
	}

	void afterConnectionClose(CON connectionKey, @Nullable Throwable t) {
		if (log.isTraceEnabled()) {
			log.trace("After connection close with key [" + connectionKey + "]");
		}
		ConnectionInfo connectionInfo = this.openConnections.remove(connectionKey);
		if (isCurrent(connectionInfo)) {
			this.currentConnection.remove();
		}
		if (connectionInfo == null) {
			// connection is already closed
			return;
		}
		connectionInfo.nestedResultSetSpans.values().forEach(SpanAndScope::close);
		connectionInfo.nestedStatements.values().forEach(statementInfo -> {
			SpanAndScope statementSpan = statementInfo.span;
			if (statementSpan != null) {
				statementSpan.close();
			}
		});
		if (log.isTraceEnabled()) {
			log.trace("Current span after closing statements [" + getTracer().currentSpan() + "]");
		}
		SpanAndScope connectionSpan = connectionInfo.span;
		if (connectionSpan != null) {
			if (t != null) {
				connectionSpan.getSpan().error(t);
			}
			if (log.isTraceEnabled()) {
				log.trace("Closing span after connection close [" + connectionSpan.getSpan() + "] - current span is ["
						+ getTracer().currentSpan() + "]");
			}
			connectionSpan.close();
			if (log.isTraceEnabled()) {
				log.trace("Current span [" + getTracer().currentSpan() + "]");
			}
		}
	}

	/**
	 * This attempts to get the ip and port from the JDBC URL. Ex. localhost and 5555 from
	 * {@code
	 * jdbc:mysql://localhost:5555/mydatabase}.
	 *
	 * Taken from Brave.
	 */
	private void parseAndSetServerIpAndPort(ConnectionInfo connectionInfo, Connection connection,
			String dataSourceName) {
		URI url = null;
		String remoteServiceName = "";
		try {
			String urlAsString = connection.getMetaData().getURL().substring(5); // strip
																					// "jdbc:"
			url = URI.create(urlAsString.replace(" ", "")); // Remove all white space
															// according to RFC 2396;
			Matcher matcher = URL_SERVICE_NAME_FINDER.matcher(url.toString());
			if (matcher.find() && matcher.groupCount() == 1) {
				String parsedServiceName = matcher.group(1);
				if (parsedServiceName != null && !parsedServiceName.isEmpty()) {
					remoteServiceName = parsedServiceName;
				}
			}
			if (!StringUtils.hasText(remoteServiceName)) {
				String databaseName = connection.getCatalog();
				if (databaseName != null && !databaseName.isEmpty()) {
					remoteServiceName = databaseName;
				}
			}
		}
		catch (Exception e) {
			// remote address is optional
		}
		connectionInfo.url = url;
		if (StringUtils.hasText(remoteServiceName)) {
			connectionInfo.remoteServiceName = remoteServiceName;
		}
		else {
			connectionInfo.remoteServiceName = dataSourceName;
		}
	}

	private Tracer getTracer() {
		if (this.tracer == null) {
			this.tracer = beanFactory.getBean(Tracer.class);
		}

		return this.tracer;
	}

	/**
	 * 连接信息
	 */
	private final class ConnectionInfo {

		/**
		 * Span
		 */
		final SpanAndScope span;

		final Map<STMT, StatementInfo> nestedStatements = new ConcurrentHashMap<>();

		final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		@Nullable
		URI url;

		@Nullable
		String remoteServiceName;

		ConnectionInfo(@Nullable SpanAndScope span) {
			this.span = span;
		}

	}

	/**
	 * 语句信息
	 */
	private final class StatementInfo {

		final SpanAndScope span;

		final Map<RS, SpanAndScope> nestedResultSetSpans = new ConcurrentHashMap<>();

		StatementInfo(SpanAndScope span) {
			this.span = span;
		}

	}

	private static final class JdbcException extends RuntimeException {

		JdbcException(String message) {
			super(message);
		}

	}

}
