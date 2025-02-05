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

package org.springframework.cloud.sleuth.instrument.async;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Function;
import java.util.function.Supplier;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ReflectionUtils;

/**
 * 将{@link Executor}包装在跟踪表示中
 *
 * <p>用于对{@link Executor}的{@link Runnable}和{@link java.util.concurrent.Callable}进行增强
 * <p>其支持Java和Spring多个执行器
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ExecutorInstrumentor {

	private static final Log log = LogFactory.getLog(ExecutorInstrumentor.class);

	/**
	 * 忽略的Bean名称列表
	 */
	private final Supplier<List<String>> ignoredBeans;

	/**
	 * Bean工厂
	 */
	private final BeanFactory beanFactory;

	public ExecutorInstrumentor(Supplier<List<String>> ignoredBeans, BeanFactory beanFactory) {
		this.ignoredBeans = ignoredBeans;
		this.beanFactory = beanFactory;
	}

	/**
	 * 仅支持以下的执行器类型:
	 * <ul>
	 *     <li>{@link Executor}</li>
	 *     <li>{@link ThreadPoolTaskExecutor}</li>
	 *     <li>{@link ScheduledExecutorService}</li>
	 *     <li>{@link ExecutorService}</li>
	 *     <li>{@link AsyncTaskExecutor}</li>
	 *     <li>{@link org.springframework.core.task.AsyncListenableTaskExecutor}</li>
	 *     <li>{@link ScheduledThreadPoolExecutor}</li>
	 *     <li>{@link ThreadPoolTaskScheduler}</li>
	 * </ul>
	 *
	 * <p>不支持以下的执行器类型：
	 * <ul>
	 *     <li>{@link LazyTraceThreadPoolTaskExecutor}</li>
	 *     <li>{@link TraceableScheduledExecutorService}</li>
	 *     <li>{@link TraceableExecutorService}</li>
	 *     <li>{@link LazyTraceAsyncTaskExecutor}</li>
	 *     <li>{@link LazyTraceExecutor}</li>
	 * </ul>
	 *
	 * @param bean bean to instrument
	 * @return {@code true} 当指定Bean适用于仪器
	 */
	public static boolean isApplicableForInstrumentation(Object bean) {
		return bean instanceof Executor && !(bean instanceof LazyTraceThreadPoolTaskExecutor
				|| bean instanceof TraceableScheduledExecutorService || bean instanceof TraceableExecutorService
				|| bean instanceof LazyTraceAsyncTaskExecutor || bean instanceof LazyTraceExecutor);
	}

	/**
	 * 在跟踪表示中包装一个{@link Executor}bean
	 *
	 * @param bean a bean (might be of {@link Executor} type
	 * @param beanName name of the bean
	 * @return wrapped bean or just bean if not {@link Executor} or already instrumented
	 */
	public Object instrument(Object bean, String beanName) {
		// 检查该bean是否可以被测量
		if (!isApplicableForInstrumentation(bean)) {
			log.info("Bean is already instrumented or is not applicable for instrumentation " + beanName);
			return bean;
		}
		if (bean instanceof ThreadPoolTaskExecutor) {
			// 处理 ThreadPoolTaskExecutor
			if (isProxyNeeded(beanName)) {
				return wrapThreadPoolTaskExecutor(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof ScheduledExecutorService) {
			// 处理 ScheduledExecutorService
			if (isProxyNeeded(beanName)) {
				return wrapScheduledExecutorService(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof ExecutorService) {
			// 处理 ExecutorService
			if (isProxyNeeded(beanName)) {
				return wrapExecutorService(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof AsyncTaskExecutor) {
			// 处理 AsyncTaskExecutor
			if (isProxyNeeded(beanName)) {
				return wrapAsyncTaskExecutor(bean, beanName);
			}
			else {
				log.info("Not instrumenting bean " + beanName);
			}
		}
		else if (bean instanceof Executor) {
			// 处理 Executor
			return wrapExecutor(bean, beanName);
		}
		return bean;
	}

	private Object wrapExecutor(Object bean, String beanName) {
		Executor executor = (Executor) bean;
		boolean methodFinal = anyFinalMethods(executor);
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		boolean cglibProxy = !methodFinal && !classFinal;
		try {
			// 为Executor创建cglib代理，代理对象为ExecutorMethodInterceptor
			return createProxy(bean, cglibProxy, new ExecutorMethodInterceptor<>(executor, this.beanFactory, beanName));
		}
		catch (AopConfigException ex) {
			if (cglibProxy) {
				if (log.isDebugEnabled()) {
					log.debug("Exception occurred while trying to create a proxy, falling back to JDK proxy", ex);
				}
				// 为Executor创建非cglib代理，代理对象为ExecutorMethodInterceptor
				return createProxy(bean, false, new ExecutorMethodInterceptor<>(executor, this.beanFactory, beanName));
			}
			throw ex;
		}
	}

	/**
	 * 包装{@link ThreadPoolTaskExecutor}
	 *
	 * @param bean
	 * @param beanName
	 * @return
	 */
	private Object wrapThreadPoolTaskExecutor(Object bean, String beanName) {
		// 类型转换为 ThreadPoolTaskExecutor
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) bean;
		// 是否为final类
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		// 是否存在public final方法
		boolean methodsFinal = anyFinalMethods(executor);
		// 非final修饰的类，且不存在任何的public final方法，即为cglib代理
		boolean cglibProxy = !classFinal && !methodsFinal;
		// 创建 ThreadPoolTaskExecutor 的代理
		return createThreadPoolTaskExecutorProxy(bean, cglibProxy, executor, beanName);
	}

	/**
	 * 包装 {@link ExecutorService}
	 *
	 * @param bean
	 * @param beanName
	 * @return
	 */
	private Object wrapExecutorService(Object bean, String beanName) {
		// 类型转换为 ExecutorService
		ExecutorService executor = (ExecutorService) bean;
		// 是否为final类
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		// 是否存在public final方法
		boolean methodFinal = anyFinalMethods(executor);
		// 非final修饰的类，且不存在任何的public final方法，即为cglib代理
		boolean cglibProxy = !classFinal && !methodFinal;
		// 创建 ExecutorService 的代理
		return createExecutorServiceProxy(bean, cglibProxy, executor, beanName);
	}

	/**
	 * 包装 {@link ScheduledExecutorService}
	 *
	 * @param bean
	 * @param beanName
	 * @return
	 */
	private Object wrapScheduledExecutorService(Object bean, String beanName) {
		// 类型转换为 ScheduledExecutorService
		ScheduledExecutorService executor = (ScheduledExecutorService) bean;
		// 是否为final类
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		// 是否存在public final方法
		boolean methodFinal = anyFinalMethods(executor);
		// 非final修饰的类，且不存在任何的public final方法，即为cglib代理
		boolean cglibProxy = !classFinal && !methodFinal;
		// 创建 ScheduledExecutorService 的代理
		return createScheduledExecutorServiceProxy(bean, cglibProxy, executor, beanName);
	}

	private Object wrapAsyncTaskExecutor(Object bean, String beanName) {
		// 类型转换为 AsyncTaskExecutor
		AsyncTaskExecutor executor = (AsyncTaskExecutor) bean;
		// 是否为final类
		boolean classFinal = Modifier.isFinal(bean.getClass().getModifiers());
		// 是否存在public final方法
		boolean methodsFinal = anyFinalMethods(executor);
		// 非final修饰的类，且不存在任何的public final方法，即为cglib代理
		boolean cglibProxy = !classFinal && !methodsFinal;
		// 创建 AsyncTaskExecutor 的代理
		return createAsyncTaskExecutorProxy(bean, cglibProxy, executor, beanName);
	}

	/**
	 * 检查给定Bean的名称是否需要被忽略
	 *
	 * @param beanName
	 * @return
	 */
	boolean isProxyNeeded(String beanName) {
		return !this.ignoredBeans.get().contains(beanName);
	}

	Object createThreadPoolTaskExecutorProxy(Object bean, boolean cglibProxy, ThreadPoolTaskExecutor executor, String beanName) {
		if (!cglibProxy) {
			// 将ThreadPoolTaskExecutor包装为LazyTraceThreadPoolTaskExecutor
			return LazyTraceThreadPoolTaskExecutor.wrap(this.beanFactory, executor, beanName);
		}
		// 将ThreadPoolTaskExecutor包装为LazyTraceThreadPoolTaskExecutor
		return getProxiedObject(bean, beanName, true, executor, () -> LazyTraceThreadPoolTaskExecutor.wrap(this.beanFactory, executor, beanName));
	}

	Supplier<Executor> createThreadPoolTaskSchedulerProxy(ThreadPoolTaskScheduler executor, String beanName) {
		// 将ThreadPoolTaskScheduler包装为LazyTraceThreadPoolTaskScheduler
		return () -> LazyTraceThreadPoolTaskScheduler.wrap(this.beanFactory, executor, beanName);
	}

	Supplier<Executor> createScheduledThreadPoolExecutorProxy(ScheduledThreadPoolExecutor executor, String beanName) {
		// 将ScheduledThreadPoolExecutor包装为LazyTraceScheduledThreadPoolExecutor
		return () -> LazyTraceScheduledThreadPoolExecutor.wrap(executor.getCorePoolSize(), executor.getThreadFactory(), executor.getRejectedExecutionHandler(), this.beanFactory, executor, beanName);
	}

	Object createExecutorServiceProxy(Object bean, boolean cglibProxy, ExecutorService executor, String beanName) {

		return getProxiedObject(bean, beanName, cglibProxy, executor, () -> {
			if (executor instanceof ScheduledExecutorService) {
				// 将ExecutorService包装为TraceableScheduledExecutorService
				return TraceableScheduledExecutorService.wrap(this.beanFactory, executor, beanName);
			}
			// 将ExecutorService包装为TraceableExecutorService
			return TraceableExecutorService.wrap(this.beanFactory, executor, beanName);
		});
	}

	Object createScheduledExecutorServiceProxy(Object bean, boolean cglibProxy, ScheduledExecutorService executor, String beanName) {
		// 将ScheduledExecutorService包装为TraceableScheduledExecutorService
		return getProxiedObject(bean, beanName, cglibProxy, executor, () -> TraceableScheduledExecutorService.wrap(this.beanFactory, executor, beanName));
	}

	Object createAsyncTaskExecutorProxy(Object bean, boolean cglibProxy, AsyncTaskExecutor executor, String beanName) {
		return getProxiedObject(bean, beanName, cglibProxy, executor, () -> {
			if (bean instanceof ThreadPoolTaskScheduler) {
				// 将ThreadPoolTaskScheduler包装为LazyTraceThreadPoolTaskScheduler
				return LazyTraceThreadPoolTaskScheduler.wrap(this.beanFactory, (ThreadPoolTaskScheduler) executor, beanName);
			}
			// 将AsyncTaskExecutor包装为LazyTraceAsyncTaskExecutor
			return LazyTraceAsyncTaskExecutor.wrap(this.beanFactory, executor, beanName);
		});
	}

	private Object getProxiedObject(Object bean, String beanName, boolean cglibProxy, Executor executor, Supplier<Executor> supplier) {
		ProxyFactoryBean factory = proxyFactoryBean(bean, beanName, cglibProxy, executor, supplier);
		try {
			return getObject(factory);
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Exception occurred while trying to get a proxy. Will fallback to a different implementation",
						ex);
			}
			try {
				if (bean instanceof ThreadPoolTaskScheduler) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ThreadPoolTaskScheduler in its tracing representation due to previous errors");
					}
					return createThreadPoolTaskSchedulerProxy((ThreadPoolTaskScheduler) bean, beanName).get();
				}
				else if (bean instanceof ScheduledThreadPoolExecutor) {
					if (log.isDebugEnabled()) {
						log.debug(
								"Will wrap ScheduledThreadPoolExecutor in its tracing representation due to previous errors");
					}
					return createScheduledThreadPoolExecutorProxy((ScheduledThreadPoolExecutor) bean, beanName).get();
				}
			}
			catch (Exception ex2) {
				if (log.isDebugEnabled()) {
					log.debug("Fallback for special wrappers failed, will try the tracing representation instead", ex2);
				}
			}
			return supplier.get();
		}
	}

	private ProxyFactoryBean proxyFactoryBean(Object bean, String beanName, boolean cglibProxy, Executor executor, Supplier<Executor> supplier) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(new ExecutorMethodInterceptor<Executor>(executor, this.beanFactory, beanName) {
			@Override
			Executor executor(BeanFactory beanFactory, Executor executor, String beanName) {
				return executorFromCache(beanFactory, executor, beanName, e -> supplier.get());
			}
		});
		factory.setTarget(bean);
		return factory;
	}

	Object getObject(ProxyFactoryBean factory) {
		return factory.getObject();
	}

	@SuppressWarnings("unchecked")
	Object createProxy(Object bean, boolean cglibProxy, Advice advice) {
		ProxyFactoryBean factory = new ProxyFactoryBean();
		factory.setProxyTargetClass(cglibProxy);
		factory.addAdvice(advice);
		factory.setTarget(bean);
		return getObject(factory);
	}

	/**
	 * 该对象中是否存在任意方法为public final方法
	 *
	 * @param object
	 * @return
	 * @param <T>
	 */
	private static <T> boolean anyFinalMethods(T object) {
		try {
			for (Method method : ReflectionUtils.getAllDeclaredMethods(object.getClass())) {
				// 忽略Object的方法
				if (method.getDeclaringClass().equals(Object.class)) {
					continue;
				}
				// 通过反射获取方法
				Method m = ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
				// 满足public final即为true
				if (m != null && Modifier.isPublic(m.getModifiers()) && Modifier.isFinal(m.getModifiers())) {
					return true;
				}
			}
		}
		catch (IllegalAccessError er) {
			if (log.isDebugEnabled()) {
				log.debug("Error occurred while trying to access methods", er);
			}
			return false;
		}
		return false;
	}

}

/**
 * 执行器方法的拦截器
 *
 * <p>用于对{@link Executor}中的方法进行拦截，确保所执行的是包装后的{@link LazyTraceExecutor}
 *
 * @param <T> - executor type
 * @author Marcin Grzejszczak
 */
class ExecutorMethodInterceptor<T extends Executor> implements MethodInterceptor {

	private final T delegate;

	private final BeanFactory beanFactory;

	private final String beanName;

	private static final Map<Executor, Executor> CACHE = new ConcurrentHashMap<>();

	ExecutorMethodInterceptor(T delegate, BeanFactory beanFactory, String beanName) {
		this.delegate = delegate;
		this.beanFactory = beanFactory;
		this.beanName = beanName;
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// 获取LazyTraceExecutor
		T executor = executor(this.beanFactory, this.delegate, this.beanName);
		// 获取方法
		Method methodOnTracedBean = getMethod(invocation, executor);
		if (methodOnTracedBean != null) {
			try {
				// 反射调用方法
				return methodOnTracedBean.invoke(executor, invocation.getArguments());
			}
			catch (InvocationTargetException ex) {
				// gh-1092: throw the target exception (if present)
				Throwable cause = ex.getCause();
				throw (cause != null) ? cause : ex;
			}
		}
		// 执行原始调用
		return invocation.proceed();
	}

	private Method getMethod(MethodInvocation invocation, Object object) {
		Method method = invocation.getMethod();
		return ReflectionUtils.findMethod(object.getClass(), method.getName(), method.getParameterTypes());
	}

	@SuppressWarnings("unchecked")
	T executor(BeanFactory beanFactory, T executor, String beanName) {
		// 将任意Executor包装为LazyTraceExecutor
		return executorFromCache(beanFactory, executor, beanName, e -> (T) LazyTraceExecutor.wrap(beanFactory, e, beanName));
	}

	@SuppressWarnings("unchecked")
	T executorFromCache(BeanFactory beanFactory, T executor, String beanName, Function<Executor, Executor> function) {
		return (T) CACHE.computeIfAbsent(executor, function);
	}

}
