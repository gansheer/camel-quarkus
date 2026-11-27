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
package org.apache.camel.quarkus.component.langchain4j.content.retriever.it;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

import static org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelContentRetrieverConstants.QUERY_PARAMS;

public class RetrieverRoutes extends RouteBuilder {

    @Override
    public void configure() {
        // the caller identity selects the data
        from("direct:orders")
                .process(e -> {
                    Map<?, ?> params = e.getProperty(QUERY_PARAMS, Map.class);
                    Map<String, Object> order = new LinkedHashMap<>();
                    order.put("orderId", "1001");
                    order.put("customer", params != null ? params.get("customerId") : null);
                    order.put("status", "DELAYED");
                    e.getMessage().setBody(List.of(order));
                });

        // the query text drives the lookup; three hits, capped by maxResults
        from("direct:kb")
                .process(e -> {
                    String query = e.getMessage().getBody(String.class);
                    e.getMessage().setBody(List.of(
                            "KB article 1 for '" + query + "'",
                            "KB article 2 for '" + query + "'",
                            "KB article 3 for '" + query + "'"));
                });

        from("direct:broken").throwException(new IllegalStateException("source down"));
    }
}
