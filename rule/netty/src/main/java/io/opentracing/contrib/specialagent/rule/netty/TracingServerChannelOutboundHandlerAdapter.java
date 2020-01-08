/* Copyright 2020 The OpenTracing Authors
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

package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpResponse;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public class TracingServerChannelOutboundHandlerAdapter extends ChannelOutboundHandlerAdapter {
  @Override
  public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise prm) {
    final Span span = ctx.channel().attr(TracingServerChannelInboundHandlerAdapter.SERVER_ATTRIBUTE_KEY).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    final HttpResponse response = (HttpResponse) msg;

    try {
      ctx.write(msg, prm);
    } catch (final Throwable throwable) {
      TracingServerChannelInboundHandlerAdapter.onError(throwable, span);
      span.setTag(Tags.HTTP_STATUS, 500);
      span.finish(); // Finish the span manually since finishSpanOnClose was false
      throw throwable;
    }

    span.setTag(Tags.HTTP_STATUS, response.getStatus().code());
    span.finish(); // Finish the span manually since finishSpanOnClose was false
  }
}
