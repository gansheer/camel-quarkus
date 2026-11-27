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

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ConfiguredRetrieverProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // a retriever declared in configuration, next to the @Retriever("kb-builder") one
                "quarkus.camel.langchain4j.retriever.orders-config.uri", "direct:retriever-orders",
                "quarkus.camel.langchain4j.retriever.orders-config.document-id-key", "orderId",
                "quarkus.camel.langchain4j.retriever.orders-config.timeout", "5s",
                // an augmentor over the two retrievers and no embedding store; the only one, so the default
                "quarkus.camel.langchain4j.rag.augmentors.camel.retrievers.orders-config.description",
                "The customer's orders of the last 30 days",
                "quarkus.camel.langchain4j.rag.augmentors.camel.retrievers.kb-builder.description",
                "Support articles: delivery delays, returns, refunds");
    }
}
