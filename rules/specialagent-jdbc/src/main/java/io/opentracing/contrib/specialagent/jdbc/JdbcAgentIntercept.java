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

package io.opentracing.contrib.specialagent.jdbc;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import io.opentracing.contrib.jdbc.TracingDriver;
import io.opentracing.contrib.specialagent.AgentRuleUtil;
import io.opentracing.contrib.specialagent.EarlyReturnException;

public class JdbcAgentIntercept {
  public static AtomicReference<TracingDriver> tracingDriver = new AtomicReference<>();

  public static void isDriverAllowed(final Class<?> caller) {
    // FIXME: LS-11527
    if (JdbcAgentIntercept.class.getName().equals(caller.getName()) || TracingDriver.class.getName().equals(caller.getName()))
      throw new EarlyReturnException();
  }

  public static Connection connect(final String url, final Properties info) throws SQLException {
    if (AgentRuleUtil.callerEquals(2, TracingDriver.class.getName() + ".connect"))
      return null;

    if (tracingDriver.get() == null) {
      synchronized (tracingDriver) {
        if (tracingDriver.get() == null) {
          // Load & register the `TracingDriver`
          try {
            Class.forName(TracingDriver.class.getName());
          }
          catch (final ClassNotFoundException e) {
            throw new IllegalStateException("TracingDriver initialization failed", e);
          }

          final Enumeration<Driver> drivers = DriverManager.getDrivers();
          TracingDriver tracingDriver = null;
          while (drivers.hasMoreElements()) {
            final Driver driver = drivers.nextElement();
            if (driver instanceof TracingDriver) {
              tracingDriver = (TracingDriver)driver;
              break;
            }
          }

          if (tracingDriver == null)
            throw new IllegalStateException(TracingDriver.class.getSimpleName() + " initialization failed");

          JdbcAgentIntercept.tracingDriver.set(tracingDriver);
        }
      }
    }

    return tracingDriver.get().connect(!url.startsWith("jdbc:tracing:") ? "jdbc:tracing:" + url.substring(5) : url, info);
  }
}