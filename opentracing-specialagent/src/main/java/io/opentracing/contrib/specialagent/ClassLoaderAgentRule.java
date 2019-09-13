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

package io.opentracing.contrib.specialagent;

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import io.opentracing.contrib.specialagent.BootLoaderAgent.Mutex;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Extendable;
import net.bytebuddy.agent.builder.AgentBuilder.Identified.Narrowable;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.utility.JavaModule;

/**
 * The {@code ClassLoaderAgent} instruments the {@code ClassLoader} class to
 * achieve direct loading of bytecode into target class loaders, which is
 * necessary for the SpecialAgent to be able to load instrumentation classes
 * into class loaders.
 *
 * @author Seva Safris
 */
public class ClassLoaderAgentRule extends AgentRule {
  public enum LocalLevel {
    SEVERE,
    FINE,
    FINEST
  }

  public static Logger logger;
  public static final ClassFileLocator locatorProxy = BootLoaderAgent.cachedLocator;
  public static Boolean isAgentRunner;

  public static void log(final String message, final Throwable thrown, final LocalLevel level) {
    if (isAgentRunner == null ? isAgentRunner = SpecialAgent.isAgentRunner() : isAgentRunner) {
      final String logLevel = System.getProperty("sa.log.level");
      if (level == LocalLevel.SEVERE || logLevel != null && logLevel.startsWith("FINE")) {
        System.err.println(message);
        if (thrown != null)
          thrown.printStackTrace(System.err);
      }
    }
    else {
      if (logger == null)
        logger = Logger.getLogger(ClassLoaderAgentRule.class);

      if (level == LocalLevel.SEVERE)
        logger.log(Level.SEVERE, message, thrown);
      else if (level == LocalLevel.FINE && logger.isLoggable(Level.FINE))
        logger.log(Level.FINE, message, thrown);
      else if (level == LocalLevel.FINEST && logger.isLoggable(Level.FINEST))
        logger.log(Level.FINEST, message, thrown);
    }
  }

  @Override
  public Iterable<? extends AgentBuilder> buildAgent(final AgentBuilder builder) throws Exception {
    log("\n<<<<<<<<<<<<<<<<< Installing ClassLoaderAgent >>>>>>>>>>>>>>>>>>\n", null, LocalLevel.FINE);

    final Narrowable narrowable = builder.type(isSubTypeOf(ClassLoader.class).and(not(is(RuleClassLoader.class)).and(not(is(PluginsClassLoader.class)))));
    final List<Extendable> builders = Arrays.asList(
      narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(DefineClass.class, locatorProxy) : Advice.to(DefineClass.class);
          return builder.visit(advice.on(named("defineClass").and(returns(Class.class).and(takesArgument(0, String.class)))));
        }}),
      narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindClass.class, locatorProxy) : Advice.to(FindClass.class);
          return builder.visit(advice.on(named("findClass").and(returns(Class.class).and(takesArguments(String.class)))));
        }}),
      narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindResource.class, locatorProxy) : Advice.to(FindResource.class);
          return builder.visit(advice.on(named("findResource").and(returns(URL.class).and(takesArguments(String.class)))));
        }}),
      narrowable.transform(new Transformer() {
        @Override
        public Builder<?> transform(final Builder<?> builder, final TypeDescription typeDescription, final ClassLoader classLoader, final JavaModule module) {
          final Advice advice = locatorProxy != null ? Advice.to(FindResources.class, locatorProxy) : Advice.to(FindResources.class);
          return builder.visit(advice.on(named("findResources").and(returns(Enumeration.class).and(takesArguments(String.class)))));
        }}));

    log("\n>>>>>>>>>>>>>>>>>> Installed ClassLoaderAgent <<<<<<<<<<<<<<<<<<\n", null, LocalLevel.FINE);
    return builders;
  }

  public static class DefineClass {
    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz) {
      SpecialAgent.preLoad(thiz);
    }
  }

  public static class FindClass {
    public static final Mutex mutex = new Mutex();

    @SuppressWarnings("unused")
    @Advice.OnMethodExit(onThrowable = ClassNotFoundException.class)
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Class<?> returned, @Advice.Thrown(readOnly = false, typing = Typing.DYNAMIC) ClassNotFoundException thrown) {
//      System.err.println(">>>>>>># findClass(" + SpecialAgentUtil.getIdentityCode(thiz) + ", \"" + arg + "\"): " + returned);
      final Set<String> visited;
      if (returned != null || !(visited = mutex.get()).add(name))
        return;

      for (final StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace())
        if (stackTraceElement.getClassName().startsWith("net.bytebuddy."))
          return;

      try {
        final byte[] bytecode = SpecialAgent.findClass(thiz, name);
        if (bytecode == null)
          return;

        log("<<<<<<<< defineClass(\"" + name + "\")", null, LocalLevel.FINEST);

        final Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
        returned = (Class<?>)defineClass.invoke(thiz, name, bytecode, 0, bytecode.length, null);
        thrown = null;
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.FindClass#exit(\"" + name + "\")", t, LocalLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class FindResource {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) URL returned) {
      final Set<String> visited;
      if (returned != null || !(visited = mutex.get()).add(name))
        return;

      for (final StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace())
        if (stackTraceElement.getClassName().startsWith("net.bytebuddy."))
          return;

      if (name.startsWith("io/lettuce/core/api/Stateful"))
        new Exception().printStackTrace();

      try {
        final URL resource = SpecialAgent.findResource(thiz, name);
        if (resource != null)
          returned = resource;
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.FindResource#exit", t, LocalLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }

  public static class FindResources {
    public static final Mutex mutex = new Mutex();

    @Advice.OnMethodExit
    public static void exit(final @Advice.This ClassLoader thiz, final @Advice.Argument(0) String name, @Advice.Return(readOnly=false, typing=Typing.DYNAMIC) Enumeration<URL> returned) {
      final Set<String> visited;
      if (!(visited = mutex.get()).add(name))
        return;

      for (final StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace())
        if (stackTraceElement.getClassName().startsWith("net.bytebuddy."))
          return;

      try {
        final Enumeration<URL> resources = SpecialAgent.findResources(thiz, name);
        if (resources == null)
          return;

        returned = returned == null ? resources : new CompoundEnumeration<>(returned, resources);
      }
      catch (final Throwable t) {
        log("<><><><> ClassLoaderAgent.FindResources#exit", t, LocalLevel.SEVERE);
      }
      finally {
        visited.remove(name);
      }
    }
  }
}