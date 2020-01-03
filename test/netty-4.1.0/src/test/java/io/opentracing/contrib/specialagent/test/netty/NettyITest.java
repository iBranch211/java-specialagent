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

package io.opentracing.contrib.specialagent.test.netty;


import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.opentracing.contrib.specialagent.TestUtil;

public class NettyITest {
  public static void main(final String[] args) throws Exception {
    TestUtil.initTerminalExceptionHandler();

    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.option(ChannelOption.SO_BACKLOG, 1024);
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel)  {
              ChannelPipeline p = socketChannel.pipeline();
              p.addLast(new HttpServerCodec());
              p.addLast(new HttpServerHandler());
            }
          });

      b.bind(8086).sync().channel();

      client();

    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }

    TestUtil.checkSpan("netty", 2);
  }

  private static void client() throws Exception {
    // Configure the client.
    EventLoopGroup group = new NioEventLoopGroup();
    try {
      Bootstrap b = new Bootstrap();
      b.group(group)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel socketChannel) throws Exception {
              ChannelPipeline p = socketChannel.pipeline();
              p.addLast(new HttpClientCodec());
              p.addLast(new HttpContentDecompressor());
              p.addLast(new HttpClientHandler());
            }
          });

      // Make the connection attempt.
      Channel ch = b.connect("127.0.0.1", 8086).sync().channel();

      // Prepare the HTTP request.
      HttpRequest request = new DefaultFullHttpRequest(
          HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER);
      request.headers().set(HttpHeaderNames.HOST, "127.0.0.1");
      request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

      // Send the HTTP request.
      ch.writeAndFlush(request);

      // Wait for the server to close the connection.
      ch.closeFuture().sync();
    } finally {
      // Shut down executor threads to exit.
      group.shutdownGracefully();
    }
  }
}