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
package io.opentracing.contrib.specialagent.elasticsearch;

import static org.awaitility.Awaitility.*;
import static org.elasticsearch.common.xcontent.XContentFactory.*;
import static org.hamcrest.core.IsEqual.*;
import static org.junit.Assert.*;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty4Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.contrib.specialagent.AgentRunner;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;

@RunWith(AgentRunner.class)
@AgentRunner.Config(isolateClassLoader = false)
public class Elasticsearch6Test {
  private static final int HTTP_PORT = 9205;
  private static final String HTTP_TRANSPORT_PORT = "9305";
  private static final String ES_WORKING_DIR = "target/es";
  private static final String clusterName = "cluster-name";
  private static Node node;

  @BeforeClass
  public static void startElasticsearch() throws Exception {
    final Settings settings = Settings.builder()
      .put("path.home", ES_WORKING_DIR)
      .put("path.data", ES_WORKING_DIR + "/data")
      .put("path.logs", ES_WORKING_DIR + "/logs")
      .put("transport.type", "netty4")
      .put("http.type", "netty4")
      .put("cluster.name", clusterName)
      .put("http.port", HTTP_PORT)
      .put("transport.tcp.port", HTTP_TRANSPORT_PORT)
      .put("network.host", "127.0.0.1")
      .build();
    final Collection<Class<? extends Plugin>> plugins = Collections.singletonList(Netty4Plugin.class);
    node = new PluginConfigurableNode(settings, plugins);
    node.start();
  }

  @AfterClass
  public static void stopElasticsearch() throws Exception {
    if (node != null)
      node.close();
  }

  @Before
  public void before(MockTracer tracer) {
    tracer.reset();
  }

  @Test
  public void restClient(final MockTracer tracer) throws Exception {
    try (final RestClient client = RestClient.builder(new HttpHost("localhost", HTTP_PORT, "http")).build()) {
      final HttpEntity entity = new NStringEntity(
        "{\n" +
        "    \"user\" : \"kimchy\",\n" +
        "    \"post_date\" : \"2009-11-15T14:12:12\",\n" +
        "    \"message\" : \"trying out Elasticsearch\"\n" +
        "}", ContentType.APPLICATION_JSON);

      Request request = new Request("PUT", "/twitter/tweet/1");
      request.setEntity(entity);

      final Response indexResponse = client.performRequest(request);

      assertNotNull(indexResponse);

      request = new Request("PUT", "/twitter/tweet/2");
      request.setEntity(entity);

      client.performRequestAsync(request, new ResponseListener() {
        @Override
        public void onSuccess(final Response response) {
        }

        @Override
        public void onFailure(final Exception exception) {
        }
      });

      await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  @Test
  public void transportClient(final MockTracer tracer) throws Exception {
    final Settings settings = Settings.builder().put("cluster.name", clusterName).build();
    try (final TransportClient client = new PreBuiltTransportClient(settings)) {
      client.addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), Integer.parseInt(HTTP_TRANSPORT_PORT)));
      final IndexRequest indexRequest = new IndexRequest("twitter")
        .type("tweet")
        .id("1")
        .source(jsonBuilder()
          .startObject()
          .field("user", "kimchy")
          .field("postDate", new Date())
          .field("message", "trying out Elasticsearch")
          .endObject());

      final IndexResponse indexResponse = client.index(indexRequest).actionGet();
      assertNotNull(indexResponse);

      client.index(indexRequest, new ActionListener<IndexResponse>() {
        @Override
        public void onResponse(final IndexResponse indexResponse) {
        }

        @Override
        public void onFailure(final Exception e) {
        }
      });

      await().atMost(15, TimeUnit.SECONDS).until(reportedSpansSize(tracer), equalTo(2));
    }

    final List<MockSpan> finishedSpans = tracer.finishedSpans();
    assertEquals(2, finishedSpans.size());
  }

  private static Callable<Integer> reportedSpansSize(final MockTracer tracer) {
    return new Callable<Integer>() {
      @Override
      public Integer call() {
        return tracer.finishedSpans().size();
      }
    };
  }

  private static class PluginConfigurableNode extends Node {
    public PluginConfigurableNode(final Settings settings, final Collection<Class<? extends Plugin>> classpathPlugins) {
      super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins, true);
    }

    @Override
    protected void registerDerivedNodeNameWithLogger(final String s) {
    }
  }
}