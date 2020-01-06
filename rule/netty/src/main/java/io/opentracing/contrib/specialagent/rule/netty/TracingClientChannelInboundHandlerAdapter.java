package io.opentracing.contrib.specialagent.rule.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AttributeKey;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

// ClientResponse
public class TracingClientChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter {
  public static final AttributeKey<Span> CLIENT_ATTRIBUTE_KEY = AttributeKey.valueOf(TracingClientChannelInboundHandlerAdapter.class.getName() + ".span");

  @Override
  public void channelRead(final ChannelHandlerContext ctx, final Object msg) {

    final Span span = ctx.channel().attr(CLIENT_ATTRIBUTE_KEY).get();

    final boolean finishSpan = msg instanceof HttpResponse;
    Scope scope = null;

    if (span != null && finishSpan) {
      scope = GlobalTracer.get().activateSpan(span);
      span.setTag(Tags.HTTP_STATUS, ((HttpResponse) msg).status().code());
    }

    try {
      ctx.fireChannelRead(msg);
    } finally {
      if (span != null && scope != null) {
        scope.close();
        span.finish();
      }
    }
  }
}
