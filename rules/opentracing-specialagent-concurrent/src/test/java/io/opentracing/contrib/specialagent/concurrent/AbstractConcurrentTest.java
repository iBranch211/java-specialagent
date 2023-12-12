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

package io.opentracing.contrib.specialagent.concurrent;

import static org.junit.Assert.*;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;

import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

/**
 * @author Pavol Loffay
 * @author Jose Montoya
 * @author Seva Safris
 */
public abstract class AbstractConcurrentTest {
  protected void assertParentSpan(final MockTracer tracer, final MockSpan parent) {
    for (final MockSpan child : tracer.finishedSpans()) {
      if (child == parent)
        continue;

      if (parent == null) {
        assertEquals(0, child.parentId());
      }
      else {
        assertEquals(parent.context().traceId(), child.context().traceId());
        assertEquals(parent.context().spanId(), child.parentId());
      }
    }
  }

  @Before
  public void reset(final MockTracer tracer) {
    tracer.reset();
  }

  class TestRunnable implements Runnable {
    private final Tracer tracer;
    private final CountDownLatch countDownLatch;

    TestRunnable(final Tracer tracer, final CountDownLatch countDownLatch) {
      this.tracer = tracer;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
      try {
        tracer.buildSpan("childRunnable").startActive(true).close();
      }
      finally {
        countDownLatch.countDown();
      }
    }
  }

  class TestCallable implements Callable<Void> {
    private final Tracer tracer;
    private final CountDownLatch countDownLatch;

    TestCallable(final Tracer tracer, final CountDownLatch countDownLatch) {
      this.tracer = tracer;
      this.countDownLatch = countDownLatch;
    }

    @Override
    public Void call() throws Exception {
      try {
        tracer.buildSpan("childCallable").startActive(true).close();
        return null;
      }
      finally {
        countDownLatch.countDown();
      }
    }
  }
}