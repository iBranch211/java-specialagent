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

package io.opentracing.contrib.specialagent.test.spring.web;

import io.opentracing.contrib.specialagent.TestUtil.ComponentSpanCount;
import org.springframework.web.client.RestTemplate;

import io.opentracing.contrib.specialagent.TestUtil;

public class SpringWebITest {
  public static void main(final String[] args) throws Exception {
    final RestTemplate restTemplate = new RestTemplate();
    restTemplate.getForObject("http://www.google.com", String.class);

    TestUtil.checkSpan(new ComponentSpanCount("java-spring-rest-template", 1));
  }
}