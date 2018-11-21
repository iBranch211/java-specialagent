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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Logger;

import org.junit.Test;

public class FingerprintTest {
  private static final Logger logger = Logger.getLogger(FingerprintTest.class.getName());

  @Test
  public void test() throws IOException {
    final Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources("javax/el/ELClass.class");
    URL jarURL = null;
    while (urls.hasMoreElements() && jarURL == null) {
      final URL url = urls.nextElement();
      if ("jar".equals(url.getProtocol()))
        jarURL = new URL("file", "", url.getPath().substring(5, url.getPath().indexOf('!')));
    }

    if (jarURL == null)
      fail("Could not find JAR resource");

    final LibraryFingerprint lib = new LibraryFingerprint(jarURL);
    logger.fine(lib.toString());
    assertEquals(37, lib.getClasses().length);

    ClassFingerprint digest;
    ConstructorFingerprint constructor;
    MethodFingerprint method;

    digest = lib.getClasses()[0];
    assertEquals("javax.el.ArrayELResolver", digest.getName());
    assertEquals("javax.el.ELResolver", digest.getSuperClass());

    assertNull(digest.getFields());

    assertEquals(2, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());
    constructor = digest.getConstructors()[1];
    assertEquals(1, constructor.getParameterTypes().length);
    assertEquals("boolean", constructor.getParameterTypes()[0]);
    assertNull(constructor.getExceptionTypes());

    assertEquals(6, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[1];
    assertNull(method.getExceptionTypes());
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[2];
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[3];
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[4];
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[5];
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertEquals("java.lang.Object", method.getParameterTypes()[3]);
    assertNull(method.getExceptionTypes());

    digest = lib.getClasses()[1];
    assertEquals("javax.el.BeanELResolver", digest.getName());
    assertEquals("javax.el.ELResolver", digest.getSuperClass());

    assertNull(digest.getFields());

    assertEquals(2, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());
    constructor = digest.getConstructors()[1];
    assertEquals(1, constructor.getParameterTypes().length);
    assertEquals("boolean", constructor.getParameterTypes()[0]);
    assertNull(constructor.getExceptionTypes());

    assertEquals(7, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[1];
    assertNull(method.getExceptionTypes());
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[2];
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[3];
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[4];
    assertEquals("invoke", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(5, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertEquals("java.lang.Class[]", method.getParameterTypes()[3]);
    assertEquals("java.lang.Object[]", method.getParameterTypes()[4]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[5];
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[6];
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertEquals("java.lang.Object", method.getParameterTypes()[3]);
    assertNull(method.getExceptionTypes());

    digest = lib.getClasses()[2];
    assertEquals("javax.el.BeanELResolver$BeanProperties", digest.getName());
    assertNull(digest.getSuperClass());

    assertNull(digest.getFields());

    assertEquals(1, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertEquals(1, constructor.getParameterTypes().length);
    assertEquals("java.lang.Class", constructor.getParameterTypes()[0]);
    assertNull(constructor.getExceptionTypes());

    assertEquals(1, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("getBeanProperty", method.getName());
    assertEquals("javax.el.BeanELResolver$BeanProperty", method.getReturnType());
    assertEquals(1, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertNull(method.getExceptionTypes());

    digest = lib.getClasses()[3];
    assertEquals("javax.el.BeanELResolver$BeanProperty", digest.getName());
    assertNull(digest.getSuperClass());

    assertNull(digest.getFields());

    assertEquals(1, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertEquals(2, constructor.getParameterTypes().length);
    assertEquals("java.lang.Class", constructor.getParameterTypes()[0]);
    assertEquals("java.beans.PropertyDescriptor", constructor.getParameterTypes()[1]);
    assertNull(constructor.getExceptionTypes());

    assertEquals(4, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("getPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[1];
    assertEquals("getReadMethod", method.getName());
    assertEquals("java.lang.reflect.Method", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[2];
    assertEquals("getWriteMethod", method.getName());
    assertEquals("java.lang.reflect.Method", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[3];
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertNull(method.getParameterTypes());
    assertNull(method.getExceptionTypes());

    digest = lib.getClasses()[4];
    assertEquals("javax.el.BeanNameELResolver", digest.getName());
    assertEquals("javax.el.ELResolver", digest.getSuperClass());

    assertNull(digest.getFields());

    assertEquals(1, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertEquals(1, constructor.getParameterTypes().length);
    assertEquals("javax.el.BeanNameResolver", constructor.getParameterTypes()[0]);
    assertNull(constructor.getExceptionTypes());

    assertEquals(6, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("getCommonPropertyType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[1];
    assertEquals("getFeatureDescriptors", method.getName());
    assertEquals("java.util.Iterator", method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[2];
    assertEquals("getType", method.getName());
    assertEquals("java.lang.Class", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[3];
    assertEquals("getValue", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[4];
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(3, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[5];
    assertEquals("setValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(4, method.getParameterTypes().length);
    assertEquals("javax.el.ELContext", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals("java.lang.Object", method.getParameterTypes()[2]);
    assertEquals("java.lang.Object", method.getParameterTypes()[3]);
    assertNull(method.getExceptionTypes());

    digest = lib.getClasses()[5];
    assertEquals("javax.el.BeanNameResolver", digest.getName());

    assertNull(digest.getFields());

    assertEquals(1, digest.getConstructors().length);
    constructor = digest.getConstructors()[0];
    assertNull(constructor.getParameterTypes());
    assertNull(constructor.getExceptionTypes());

    assertEquals(5, digest.getMethods().length);

    method = digest.getMethods()[0];
    assertEquals("canCreateBean", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[1];
    assertEquals("getBean", method.getName());
    assertEquals("java.lang.Object", method.getReturnType());
    assertEquals(1, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[2];
    assertEquals("isNameResolved", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[3];
    assertEquals("isReadOnly", method.getName());
    assertEquals("boolean", method.getReturnType());
    assertEquals(1, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertNull(method.getExceptionTypes());

    method = digest.getMethods()[4];
    assertEquals("setBeanValue", method.getName());
    assertNull(method.getReturnType());
    assertEquals(2, method.getParameterTypes().length);
    assertEquals("java.lang.String", method.getParameterTypes()[0]);
    assertEquals("java.lang.Object", method.getParameterTypes()[1]);
    assertEquals(1, method.getExceptionTypes().length);
    assertEquals("javax.el.PropertyNotWritableException", method.getExceptionTypes()[0]);
  }
}