/*
 * Copyright 2016-2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.web.servlet.filter;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.Span;

/**
 * SpanDecorator to decorate span at different stages in filter processing (before filterChain.doFilter(), after and
 * if exception is thrown).
 *
 * @author Pavol Loffay
 */
public interface ServletFilterSpanDecorator {

    /**
     * Decorate span before {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)} is
     * called. This is called right after span in created. Span is already present in request attributes with name
     * {@link TracingFilter#SERVER_SPAN_CONTEXT}.
     *
     * @param httpServletRequest request
     * @param span span to decorate
     */
    void onRequest(HttpServletRequest httpServletRequest, Span span);

    /**
     * Decorate span after {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}. When it
     * is an async request this will be called in {@link javax.servlet.AsyncListener#onComplete(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param httpServletResponse response
     * @param span span to decorate
     */
    void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Span span);

    /**
     * Decorate span when an exception is thrown during processing in
     * {@link javax.servlet.Filter#doFilter(ServletRequest, ServletResponse, FilterChain)}. This is
     * also called in {@link javax.servlet.AsyncListener#onError(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param exception exception
     * @param span span to decorate
     */
    void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                 Throwable exception, Span span);

    /**
     * Decorate span on asynchronous request timeout. It is called in
     * {@link javax.servlet.AsyncListener#onTimeout(javax.servlet.AsyncEvent)}.
     *
     * @param httpServletRequest request
     * @param httpServletResponse response
     * @param timeout timeout
     * @param span span to decorate
     */
    void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                 long timeout, Span span);

    ServletFilterSpanDecorator STANDARD_TAGS = new StandardTagsServletFilterSpanDecorator();
  }
