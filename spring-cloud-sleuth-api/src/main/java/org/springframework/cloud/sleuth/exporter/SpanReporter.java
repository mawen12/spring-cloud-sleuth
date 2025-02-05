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

package org.springframework.cloud.sleuth.exporter;

/**
 * 允许在Span完成后处理Span的接口
 *
 * @author Marcin Grzejszczak
 * @since 3.1.0
 */
public interface SpanReporter {

	/**
	 * 上报完成的Span
	 *
	 * @param span a span that was ended and is ready to be reported.
	 */
	void report(FinishedSpan span);

}
