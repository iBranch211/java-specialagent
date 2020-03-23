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

package io.opentracing.contrib.specialagent;

import java.net.URL;
import java.net.URLClassLoader;

public class BootProxyClassLoader extends URLClassLoader {
  public static final BootProxyClassLoader INSTANCE = new BootProxyClassLoader();

  private BootProxyClassLoader() {
    super(new URL[0], null);
  }

  @Override
  public Class<?> findClass(final String name) throws ClassNotFoundException {
    return super.findClass(name);
  }

  public Class<?> loadClassOrNull(final String name, final boolean resolve) {
    try {
      return super.loadClass(name, resolve);
    }
    catch (final ClassNotFoundException e) {
      return null;
    }
  }
}