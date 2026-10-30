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

import java.util.List;
import java.util.function.Supplier;

import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetriever;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_SOURCE;

@ApplicationScoped
public class ContentRetrieverAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    CamelContext camelContext;

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(
                        new CamelContentRetriever(camelContext, "orders", "direct:retriever-orders")
                                .setDocumentIdKey("orderId"),
                        new CamelContentRetriever(camelContext, "kb", "direct:retriever-kb")))
                .contentInjector(DefaultContentInjector.builder()
                        .metadataKeysToInclude(List.of(METADATA_SOURCE, METADATA_DOCUMENT_ID))
                        .build())
                .build();
    }
}
