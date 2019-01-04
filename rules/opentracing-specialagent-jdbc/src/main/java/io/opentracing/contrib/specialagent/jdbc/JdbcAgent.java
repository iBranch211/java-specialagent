package io.opentracing.contrib.specialagent.jdbc;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Properties;

import io.opentracing.contrib.specialagent.AgentPlugin;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InitializationStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

public class JdbcAgent implements AgentPlugin {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
//  buildAgent(agentArgs).installOn(inst);
  }

  @Override
  public AgentBuilder buildAgent(final String agentArgs) throws Exception {
    return new AgentBuilder.Default()
//      .with(AgentBuilder.Listener.StreamWriting.toSystemError())
      .with(RedefinitionStrategy.RETRANSFORMATION)
      .with(InitializationStrategy.NoOp.INSTANCE)
      .with(TypeStrategy.Default.REDEFINE)
      .type(hasSuperType(named("java.sql.Driver")).and(not(named("io.opentracing.contrib.jdbc.TracingDriver"))))
      .transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          return builder.visit(Advice.to(JdbcAgent.class).on(not(isAbstract()).and(named("connect").and(ElementMatchers.takesArguments(String.class, Properties.class)))));
        }});
  }

  @Advice.OnMethodExit
  public static void exit(@Advice.Origin Method method, @Advice.Return(readOnly = false, typing = Typing.DYNAMIC) Object returned) throws Exception {
    System.out.println(">>>>>> " + method);
    returned = Intercept.exit(returned);
  }
}