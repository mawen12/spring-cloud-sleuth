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

package org.springframework.cloud.sleuth.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Sleuth内部使用的工具。请勿使用。
 *
 * <p>用于监听Spring上下文的启动状态
 *
 * @author Marcin Grzejszczak
 * @since 2.2.5
 */
public class SleuthContextListener implements SmartApplicationListener {

	/**
	 * 保存了原始的Bean工厂哈希值与Sleuth上下文监听器
	 *
	 * <p>一个Bean工厂对应一个Sleuth上下文监听器
	 */
	static final Map<Integer/* Bean工厂的哈希值 */, SleuthContextListener/* Sleuth上下文监听器 */> CACHE = new ConcurrentHashMap<>();

	private static final Log log = LogFactory.getLog(SleuthContextListener.class);

	final AtomicBoolean refreshed;

	final AtomicBoolean closed;

	public SleuthContextListener() {
		this.refreshed = new AtomicBoolean();
		this.closed = new AtomicBoolean();
	}

	SleuthContextListener(AtomicBoolean refreshed, AtomicBoolean closed) {
		this.refreshed = refreshed;
		this.closed = closed;
	}

	/**
	 * 从缓存中读取{@link SleuthContextListener}，如果没有则创建新的实例并返回
	 *
	 * @param beanFactory bean factory
	 * @return instance of {@link SleuthContextListener}
	 */
	public static SleuthContextListener getBean(BeanFactory beanFactory) {
		BeanFactory bf = beanFactory;
		if (bf instanceof ConfigurableApplicationContext) {
			bf = ((ConfigurableApplicationContext) bf).getBeanFactory();
		}
		return CACHE.getOrDefault(bf.hashCode(), new SleuthContextListener());
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		// 仅支持上下文关闭和刷新事件
		return ContextClosedEvent.class.isAssignableFrom(eventType) || ContextRefreshedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ContextRefreshedEvent || event instanceof ContextClosedEvent) {
			if (log.isTraceEnabled()) {
				log.trace("Context refreshed or closed [" + event + "]");
			}
			ApplicationContextEvent contextEvent = (ApplicationContextEvent) event;
			// 获取应用上下文
			ApplicationContext context = contextEvent.getApplicationContext();
			// 转换为Bean工厂
			BeanFactory beanFactory = context;

			if (context instanceof ConfigurableApplicationContext) {
				beanFactory = ((ConfigurableApplicationContext) context).getBeanFactory();
			}
			// 获取监听器
			SleuthContextListener listener = CACHE.getOrDefault(beanFactory.hashCode(), this);
			// 更新刷新状态
			listener.refreshed.compareAndSet(false, event instanceof ContextRefreshedEvent);
			// 更新关闭状态
			listener.closed.compareAndSet(false, event instanceof ContextClosedEvent);
			// 放入缓存
			CACHE.put(beanFactory.hashCode(), listener);
		}
	}

	/**
	 * 验证上下文是否未使用
	 *
	 * @return true 当Spring上下文尚未启动
	 */
	public boolean isUnusable() {
		// 未刷新或已关闭
		return !this.refreshed.get() || this.closed.get();
	}

}
