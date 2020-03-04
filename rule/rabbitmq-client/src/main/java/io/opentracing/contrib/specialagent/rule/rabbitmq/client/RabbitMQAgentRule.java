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

package io.opentracing.contrib.specialagent.rule.rabbitmq.client;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.util.Arrays;

import io.opentracing.contrib.specialagent.AgentRule;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

public class RabbitMQAgentRule extends AgentRule {
  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    return Arrays.asList(builder
      .type(hasSuperType(named("com.rabbitmq.client.impl.AMQChannel")).and(not(named("io.opentracing.contrib.rabbitmq.TracingChannel"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnEnterPublish.class, OnExitPublish.class).on(named("basicPublish").and(takesArguments(6))));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnExitGet.class).on(named("basicGet")));
        }})
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(OnEnterConsume.class).on(named("basicConsume").and(takesArguments(7))));
        }})
        .type(not(isInterface()).and(hasSuperType(named("com.rabbitmq.client.Consumer"))).and(not(named("io.opentracing.contrib.rabbitmq.TracingConsumer"))))
        .transform(new Transformer() {
          @Override
          public Builder<?> transform(Builder<?> builder, TypeDescription typeDescription,
              ClassLoader classLoader, JavaModule module) {
            return builder.visit(Advice.to(Consumer.class).on(named("handleDelivery")));
          }
        })
    );
  }

  /**
   * It's needed for spring-rabbitmq to work in static deferred attach mode
   */
  public static class Consumer {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, @Advice.This Object thiz, final @Advice.Argument(value = 2) Object properties) {
      if (isEnabled("RabbitMQAgentRule", origin))
        RabbitMQAgentIntercept.handleDeliveryStart(thiz, properties);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
      if (isEnabled("RabbitMQAgentRule", origin))
        RabbitMQAgentIntercept.handleDeliveryEnd(thrown);
    }
  }

  public static class OnEnterConsume {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object queue, @Advice.Argument(value = 6, readOnly = false, typing = Typing.DYNAMIC) Object callback) {
      if (isEnabled("RabbitMQAgentRule", origin))
        callback = RabbitMQAgentIntercept.enterConsume(callback, queue);
    }
  }

  public static class OnEnterPublish {
    @Advice.OnMethodEnter
    public static void enter(final @Advice.Origin String origin, final @Advice.Argument(value = 0) Object exchange, final @Advice.Argument(value = 1) Object routingKey, @Advice.Argument(value = 4, readOnly = false, typing = Typing.DYNAMIC) Object props) {
      if (isEnabled("RabbitMQAgentRule", origin))
        props = RabbitMQAgentIntercept.enterPublish(exchange, routingKey, props);
    }
  }

  public static class OnExitPublish {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown) {
      if (isEnabled("RabbitMQAgentRule", origin))
        RabbitMQAgentIntercept.finish(thrown);
    }
  }

  public static class OnExitGet {
    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void exit(final @Advice.Origin String origin, final @Advice.Thrown Throwable thrown, final @Advice.Argument(value = 0) Object queue, final @Advice.Return Object returned) {
      if (isEnabled("RabbitMQAgentRule", origin))
        RabbitMQAgentIntercept.exitGet(returned, queue, thrown);
    }
  }
}