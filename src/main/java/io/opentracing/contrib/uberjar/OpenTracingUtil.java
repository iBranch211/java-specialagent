/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentracing.contrib.uberjar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class OpenTracingUtil {
  private static final int DEFAULT_SOCKET_BUFFER_SIZE = 65536;

  public static byte[] readBytes(final InputStream in) throws IOException {
    final ByteArrayOutputStream buffer = new ByteArrayOutputStream(DEFAULT_SOCKET_BUFFER_SIZE);
    final byte[] data = new byte[DEFAULT_SOCKET_BUFFER_SIZE];
    for (int len; (len = in.read(data)) != -1; buffer.write(data, 0, len));
    return buffer.toByteArray();
  }

  public static List<URL> findJarPath(final String path) throws IOException {
    final URL url = ClassLoader.getSystemClassLoader().getResource(path);
    if (url == null)
      return null;

    final JarURLConnection jarURLConnection = (JarURLConnection)url.openConnection();
    jarURLConnection.setUseCaches(false);
    final JarFile jarFile = jarURLConnection.getJarFile();

    final Path destDir = Files.createTempDirectory("opentracing");
    destDir.toFile().deleteOnExit();

    final List<URL> resources = new ArrayList<>();
    final Enumeration<JarEntry> enumeration = jarFile.entries();
    while (enumeration.hasMoreElements()) {
      final String entry = enumeration.nextElement().getName();
      if (entry.length() > path.length() && entry.startsWith(path)) {
        final int slash = entry.lastIndexOf('/');
        final File dir = new File(destDir.toFile(), entry.substring(0, slash));
        dir.mkdirs();
        dir.deleteOnExit();
        final File file = new File(dir, entry.substring(slash + 1));
        file.deleteOnExit();
        final URL u = new URL(url, entry.substring(path.length()));
        Files.copy(u.openStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        resources.add(file.toURI().toURL());
      }
    }

    return resources;
  }

  private OpenTracingUtil() {
  }
}