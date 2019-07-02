/* Copyright 2018 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentracing.contrib.specialagent.webservletfilter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.servlet.ServletContextHandler;

import io.opentracing.contrib.specialagent.AgentRule;
import io.opentracing.contrib.web.servlet.filter.TracingFilter;
import io.opentracing.util.GlobalTracer;

public class JettyAgentIntercept extends ContextAgentIntercept {
  public static final Map<Context,Object> state = Collections.synchronizedMap(new WeakHashMap<Context,Object>());

  public static void exit(final Object thiz) {
    final Context context = ((ServletContextHandler)thiz).getServletContext();
    if (state.containsKey(context))
      return;

    if (!isFilterMethodPresent(context)) {
      if (AgentRule.logger.isLoggable(Level.FINER))
        AgentRule.logger.finer("JettyAgentIntercept#exit(" + context.getClass().getName() + "): isFilterMethodPresent = false");

      return;
    }

    final TracingFilter filter = new TracingFilter(GlobalTracer.get());
    context.addFilter(TRACING_FILTER_NAME, filter).addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, patterns);
    state.put(context, null);
  }
}