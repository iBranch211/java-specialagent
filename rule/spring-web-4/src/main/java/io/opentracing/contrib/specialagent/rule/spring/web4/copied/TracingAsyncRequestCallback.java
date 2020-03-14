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
package io.opentracing.contrib.specialagent.rule.spring.web4.copied;

import io.opentracing.SpanContext;
import io.opentracing.propagation.Format.Builtin;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import org.springframework.http.client.AsyncClientHttpRequest;
import org.springframework.web.client.AsyncRequestCallback;

public class TracingAsyncRequestCallback implements AsyncRequestCallback {
  private final AsyncRequestCallback callback;
  private final SpanContext spanContext;

  public TracingAsyncRequestCallback(AsyncRequestCallback callback,
      SpanContext spanContext) {
    this.callback = callback;
    this.spanContext = spanContext;
  }

  @Override
  public void doWithRequest(AsyncClientHttpRequest request) throws IOException {
    GlobalTracer.get().inject(spanContext, Builtin.HTTP_HEADERS, new HttpHeadersCarrier(request.getHeaders()));
    callback.doWithRequest(request);
  }

}
