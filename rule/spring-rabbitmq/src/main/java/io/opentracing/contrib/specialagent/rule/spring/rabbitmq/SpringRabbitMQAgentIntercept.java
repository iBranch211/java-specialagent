/* Copyright 2019 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.spring.rabbitmq;

import io.opentracing.contrib.specialagent.LocalSpanContext;
import io.opentracing.contrib.specialagent.SpanUtil;
import java.util.Map;

import org.springframework.amqp.core.Message;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class SpringRabbitMQAgentIntercept {
  private static final ThreadLocal<LocalSpanContext> contextHolder = new ThreadLocal<>();

  public static void onMessageEnter(final Object msg) {
    if (contextHolder.get() != null) {
      contextHolder.get().increment();
      return;
    }

    final Tracer tracer = GlobalTracer.get();
    final SpanBuilder builder = tracer
      .buildSpan("onMessage")
      .withTag(Tags.COMPONENT, "spring-rabbitmq")
      .withTag(Tags.SPAN_KIND, Tags.SPAN_KIND_CONSUMER);

    final Message message = (Message)msg;
    if (message.getMessageProperties() != null) {
      final Map<String,Object> headers = message.getMessageProperties().getHeaders();
      final SpanContext spanContext = tracer.extract(Builtin.TEXT_MAP, new HeadersMapExtractAdapter(headers));
      if (spanContext != null)
        builder.addReference(References.FOLLOWS_FROM, spanContext);
    }

    final Span span = builder.start();
    contextHolder.set(new LocalSpanContext(span, tracer.activateSpan(span)));
  }

  public static void onMessageExit(Throwable thrown) {
    final LocalSpanContext context = contextHolder.get();
    if (context == null)
      return;

    if (context.decrementAndGet() != 0)
      return;

    if (thrown != null)
      SpanUtil.onError(thrown, context.getSpan());

    context.closeAndFinish();
    contextHolder.remove();
  }

}