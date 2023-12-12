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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.cli.MavenCli;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import io.opentracing.contrib.specialagent.Manager.Event;
import net.bytebuddy.agent.ByteBuddyAgent;

/**
 * A JUnit runner that is designed to run tests for instrumentation plugins that
 * have auto-instrumentation rules implemented as per the {@link AgentPlugin}
 * API.
 * <p>
 * The {@code AgentRunner} uses ByteBuddy's self-attach methodology to obtain
 * the {@code Instrumentation} instance. This architecture allows tests with the
 * {@code @RunWith(AgentRunner.class)} annotation to be run from any environment
 * (i.e. from Maven's Surefire plugin, from an IDE, or directly via JUnit
 * itself).
 * <p>
 * The {@code AgentRunner} has a facility to "raise" the classes loaded for the
 * purpose of the test into an isolated {@code ClassLoader} (see
 * {@link Config#isolateClassLoader()}). This allows the test to ensure that
 * instrumentation is successful for classes that are loaded in a
 * {@code ClassLoader} that is not the System or Bootstrap {@code ClassLoader}.
 * <p>
 * The {@code AgentRunner} also has a facility to aide in debugging of the
 * runner's runtime Please refer to {@link Config}.
 *
 * @author Seva Safris
 */
public class AgentRunner extends BlockJUnit4ClassRunner {
  private static final Logger logger = Logger.getLogger(AgentRunner.class.getName());
  private static final Instrumentation inst;

  private static JarFile createJarFileOfSource(final Class<?> cls) throws IOException {
    final String testClassesPath = cls.getProtectionDomain().getCodeSource().getLocation().getPath();
    if (logger.isLoggable(Level.FINEST))
      logger.finest("Source location (\"" + cls.getName() + "\"): " + testClassesPath);

    if (testClassesPath.endsWith("-tests.jar"))
      return new JarFile(new File(testClassesPath.substring(0, testClassesPath.length() - 10) + ".jar"));

    if (testClassesPath.endsWith(".jar"))
      return new JarFile(new File(testClassesPath));

    if (testClassesPath.endsWith("/test-classes/")) {
      return Util.createTempJarFile(new File(testClassesPath.substring(0, testClassesPath.length() - 14) + "/classes/"));
    }

    if (testClassesPath.endsWith("classes/"))
      return Util.createTempJarFile(new File(testClassesPath.endsWith("/test-classes/") ? testClassesPath.substring(0, testClassesPath.length() - 14) + "/classes/" : testClassesPath));

    throw new UnsupportedOperationException("Unsupported source path: " + testClassesPath);
  }

  static Instrumentation install() {
    if (inst != null)
      return inst;

    try {
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n>>>>>>>>>>>>>>>>>>>>>>> Installing Agent <<<<<<<<<<<<<<<<<<<<<<<\n");

      // FIXME: Can this be done in a better way?
      final JarFile jarFile = createJarFileOfSource(AgentRunner.class);
      final Instrumentation inst = ByteBuddyAgent.install();
      inst.appendToBootstrapClassLoaderSearch(jarFile);
      if (logger.isLoggable(Level.FINE))
        logger.fine("\n================== Installing BootLoaderAgent ==================\n");

      BootLoaderAgent.premain(inst, jarFile);
      return inst;
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static final File CWD = new File("").getAbsoluteFile();

  static {
    inst = install();
  }

  /**
   * Annotation to specify configuration parameters for {@code AgentRunner}.
   */
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Config {
    public enum Log {
      SEVERE(Level.SEVERE),
      WARNING(Level.WARNING),
      INFO(Level.INFO),
      CONFIG(Level.CONFIG),
      FINE(Level.FINE),
      FINER(Level.FINER),
      FINEST(Level.FINEST);

      final Level level;

      Log(final Level level) {
        this.level = level;
      }
    }

    /**
     * @return Logging level.
     *         <p>
     *         Default: {@link Log#WARNING}.
     */
    Log log() default Log.WARNING;

    /**
     * @return Output re/transformer events.
     *         <p>
     *         Default: <code>{}</code>.
     */
    Event[] events() default {};

    /**
     * @return Whether the tests should be run in a {@code ClassLoader} that is
     *         isolated from the system {@code ClassLoader}.
     *         <p>
     *         Default: {@code true}.
     */
    boolean isolateClassLoader() default true;
  }

  /**
   * Loads the specified class in an isolated {@code URLClassLoader}. The class
   * loader will be initialized with the process classpath, and will be detached
   * from the System {@code ClassLoader}. This construct guarantees that any
   * {@code cls} passed to this function will be unable to resolve classes in
   * the System {@code ClassLoader}.
   * <p>
   * <i><b>Note:</b> If {@code cls} is present in the Bootstrap
   * {@code ClassLoader}, it will be resolved in the Bootstrap
   * {@code ClassLoader} instead of the {@code URLClassLoader} created by this
   * function.</i>
   *
   * @param testClass The test class to load in the {@code URLClassLoader}.
   * @return The class loaded in the {@code URLClassLoader}.
   * @throws InitializationError If the specified class cannot be located by the
   *           {@code URLClassLoader}.
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  private static Class<?> loadClassInIsolatedClassLoader(final Class<?> testClass) throws InitializationError, InterruptedException {
    try {
      final String testClassesPath = testClass.getProtectionDomain().getCodeSource().getLocation().getPath();
      final String classesPath = testClassesPath.endsWith(".jar") ? testClassesPath.replace(".jar", "-tests.jar") : testClassesPath.replace("/test-classes/", "/classes/");
      URL dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(SpecialAgent.DEPENDENCIES_TGF);
      if (dependenciesUrl == null) {
        logger.warning(SpecialAgent.DEPENDENCIES_TGF + " was not found: invoking `mvn generate-resources`");
        System.setProperty("maven.multiModuleProjectDirectory", CWD.getParentFile().getParentFile().getAbsolutePath());
        new MavenCli().doMain(new String[] {"generate-resources"}, CWD.getAbsolutePath(), System.out, System.err);
        final File dependenciesTgf = new File(CWD, "target/generated-resources/" + SpecialAgent.DEPENDENCIES_TGF);
        if (dependenciesTgf.exists()) {
          Files.copy(dependenciesTgf.toPath(), new File(CWD, "target/classes/" + SpecialAgent.DEPENDENCIES_TGF).toPath());
          dependenciesUrl = Thread.currentThread().getContextClassLoader().getResource(SpecialAgent.DEPENDENCIES_TGF);
        }

        if (dependenciesUrl == null) {
          logger.severe(SpecialAgent.DEPENDENCIES_TGF + " was not found: Please assert that `mvn generate-resources` executes successfully");
          return Object.class;
        }
      }

      final List<String> pluginPaths = findPluginPaths(dependenciesUrl);

      pluginPaths.add(testClassesPath);
      pluginPaths.add(classesPath);
      final Set<String> isolatedClasses = TestUtil.getClassFiles(pluginPaths);

      final URL[] libs = Util.classPathToURLs(System.getProperty("java.class.path"));
      // Special case for AgentRunnerITest, because it belongs to the same
      // classpath path as the AgentRunner

      final URLClassLoader classLoader = new URLClassLoader(libs, new ClassLoader(ClassLoader.getSystemClassLoader()) {
        private final ClassLoader bootstrapClassLoader = new URLClassLoader(new URL[0], null);

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
          return isolatedClasses.contains(name.replace('.', '/').concat(".class")) ? bootstrapClassLoader.loadClass(name) : super.loadClass(name, resolve);
        }
      });

      final Class<?> classInClassLoader = Class.forName(testClass.getName(), false, classLoader);
      Assert.assertNotNull("Test class is not resolvable in URLClassLoader: " + testClass.getName(), classInClassLoader);
      Assert.assertNotNull("Test class must not be resolvable in bootstrap class loader: " + testClass.getName(), classInClassLoader.getClassLoader());
      Assert.assertEquals(URLClassLoader.class, classInClassLoader.getClassLoader().getClass());
      return classInClassLoader;
    }
    catch (final ClassNotFoundException | IOException e) {
      throw new InitializationError(e);
    }
  }

  /**
   * Find the plugin paths using the specified dependencies TGF {@code URL}.
   *
   * @param dependenciesUrl The {@code URL} pointing to the dependencies TGF
   *          file.
   * @return A list of the plugin paths.
   * @throws IOException If an I/O error has occurred.
   */
  private static List<String> findPluginPaths(final URL dependenciesUrl) throws IOException {
    final String dependenciesTgf = dependenciesUrl == null ? null : new String(Util.readBytes(dependenciesUrl));

    final List<String> pluginPaths = new ArrayList<>();
    final URL[] classpath = Util.classPathToURLs(System.getProperty("java.class.path"));

    final URL[] pluginUrls = Util.filterPluginURLs(classpath, dependenciesTgf, false, "compile");
    for (int i = 0; i < pluginUrls.length; ++i)
      pluginPaths.add(pluginUrls[i].getFile());

    // Use the whole java.class.path for the forked process, because any class
    // on the classpath may be used in the implementation of the test method.
    // The JARs with classes in the Boot-Path are already excluded due to their
    // provided scope.
    if (logger.isLoggable(Level.FINEST))
      logger.finest("PluginsPath of forked process will be:\n" + Util.toIndentedString(pluginPaths));

    System.setProperty(SpecialAgent.PLUGIN_ARG, Util.toString(pluginPaths.toArray(), ":"));

    // Add scope={"test", "provided"}, optional=true to pluginPaths
    final URL[] testDependencies = Util.filterPluginURLs(classpath, dependenciesTgf, true, "test", "provided");
    for (final URL testDependency : testDependencies)
      pluginPaths.add(testDependency.getPath());

    return pluginPaths;
  }

  private final Config config;

  /**
   * Creates a new {@code AgentRunner} for the specified test class.
   *
   * @param testClass The test class.
   * @throws InitializationError If the test class is malformed, or if the
   *           specified class cannot be located by the URLClassLoader in the
   *           forked process.
   * @throws InterruptedException If a required Maven subprocess is interrupted.
   */
  public AgentRunner(final Class<?> testClass) throws InitializationError, InterruptedException {
    super(testClass.getAnnotation(Config.class) == null || testClass.getAnnotation(Config.class).isolateClassLoader() ? loadClassInIsolatedClassLoader(testClass) : testClass);
    this.config = testClass.getAnnotation(Config.class);
    if (config != null) {
      if (config.log() != Config.Log.INFO) {
        final String logLevelProperty = System.getProperty(SpecialAgent.LOGGING_PROPERTY);
        if (logLevelProperty != null)
          logger.warning(SpecialAgent.LOGGING_PROPERTY + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".log is specified in " + testClass.getName());
        else
          System.setProperty(SpecialAgent.LOGGING_PROPERTY, String.valueOf(config.log()));
      }

      final Event[] events = config.events();
      if (events.length > 0) {
        final String eventsProperty = System.getProperty(SpecialAgent.EVENTS_PROPERTY);
        if (eventsProperty != null) {
          logger.warning(SpecialAgent.EVENTS_PROPERTY + " system property is specified on command line, and @" + AgentRunner.class.getSimpleName() + "." + Config.class.getSimpleName() + ".events is specified in " + testClass.getName());
        }
        else {
          final StringBuilder builder = new StringBuilder();
          for (final Event event : events)
            builder.append(event).append(",");

          builder.setLength(builder.length() - 1);
          System.setProperty(SpecialAgent.EVENTS_PROPERTY, builder.toString());
        }
      }
    }

    try {
      SpecialAgent.premain(null, inst);
    }
    catch (final Throwable e) {
      e.printStackTrace();
    }
  }

  private int delta = Integer.MAX_VALUE;

  static File getManifestFile() {
    return new File(CWD, "target/classes/META-INF/opentracing-specialagent/TEST-MANIFEST.MF");
  }

  @Override
  public void run(final RunNotifier notifier) {
    super.run(notifier);
    if (delta == 0) {
      try {
        final File manifestFile = getManifestFile();
        manifestFile.getParentFile().mkdirs();
        final Path path = manifestFile.toPath();
        if (!Files.exists(path))
          Files.createFile(path);
      }
      catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /**
   * Creates the {@code TestClass} object for this JUnit runner with the
   * specified test class.
   * <p>
   * This method has been overridden to retrofit the {@code FrameworkMethod}
   * objects.
   *
   * @param testClass The test class.
   * @return The {@code TestClass} object for this JUnit runner with the
   *         specified test class.
   */
  @Override
  protected TestClass createTestClass(final Class<?> testClass) {
    return new TestClass(testClass) {
      @Override
      public List<FrameworkMethod> getAnnotatedMethods(final Class<? extends Annotation> annotationClass) {
        final List<FrameworkMethod> retrofitted = new ArrayList<>();
        for (final FrameworkMethod method : super.getAnnotatedMethods(annotationClass))
          retrofitted.add(retrofitMethod(method));

        return Collections.unmodifiableList(retrofitted);
      }

      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final Map.Entry<Class<? extends Annotation>,List<FrameworkMethod>> entry : methodsForAnnotations.entrySet()) {
          final ListIterator<FrameworkMethod> iterator = entry.getValue().listIterator();
          while (iterator.hasNext())
            iterator.set(retrofitMethod(iterator.next()));
        }
      }
    };
  }

  /**
   * Retrofits the specified {@code FrameworkMethod} to work with the forked
   * testing architecture of this runner.
   *
   * @param method The {@code FrameworkMethod} to retrofit.
   * @return The retrofitted {@code FrameworkMethod}.
   */
  private FrameworkMethod retrofitMethod(final FrameworkMethod method) {
    return new FrameworkMethod(method.getMethod()) {
      @Override
      public void validatePublicVoidNoArg(final boolean isStatic, final List<Throwable> errors) {
        validatePublicVoid(isStatic, errors);
        if (method.getMethod().getParameterTypes().length > 1)
          errors.add(new Exception("Method " + method.getName() + " can declare no parameters, or one parameter of type: io.opentracing.mock.MockTracer"));
      }

      @Override
      public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
        if (delta == Integer.MAX_VALUE)
          delta = 0;

        ++delta;
        if (logger.isLoggable(Level.FINEST))
          logger.finest("invokeExplosively [" + getName() + "](" + target + ")");

        if (config == null || config.isolateClassLoader()) {
          final ClassLoader classLoader = isStatic() ? method.getDeclaringClass().getClassLoader() : target.getClass().getClassLoader();
          Assert.assertEquals("Method " + getName() + " should be executed in URLClassLoader", URLClassLoader.class, classLoader == null ? null : classLoader.getClass());
        }

        final Object object = method.getMethod().getParameterTypes().length == 1 ? super.invokeExplosively(target, AgentRunnerUtil.getTracer()) : super.invokeExplosively(target);
        --delta;
        return object;
      }
    };
  }

  /**
   * Overridden because the stock implementation does not remove null values,
   * which ends up causing a NullPointerException later down a callstack.
   */
  @Override
  protected List<TestRule> getTestRules(final Object target) {
    final List<TestRule> rules = super.getTestRules(target);
    final ListIterator<TestRule> iterator = rules.listIterator(rules.size());
    while (iterator.hasPrevious())
      if (iterator.previous() == null)
        iterator.remove();

    return rules;
  }
}