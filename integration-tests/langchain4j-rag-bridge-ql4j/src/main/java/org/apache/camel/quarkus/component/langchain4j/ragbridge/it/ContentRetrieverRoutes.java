/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.langchain4j.ragbridge.it;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.QUERY_PARAM_PREFIX;

public class ContentRetrieverRoutes extends RouteBuilder {

    @Override
    public void configure() {
        // Metadata-driven: the caller identity selects the data, the prompt text is not used
        from("direct:retriever-orders")
                .process(e -> {
                    String customerId = e.getProperty(QUERY_PARAM_PREFIX + "customerId", String.class);
                    Map<String, Object> order = new LinkedHashMap<>();
                    order.put("orderId", "1001");
                    order.put("customer", customerId);
                    order.put("status", "DELAYED");
                    e.getMessage().setBody(List.of(order));
                });

        // Template / pass-through: the prompt text drives the lookup
        from("direct:retriever-kb")
                .setBody(simple("KB article matching '${body}': delayed orders can be cancelled for a full refund"));
    }
}
