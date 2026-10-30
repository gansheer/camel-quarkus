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
import java.util.Map;
import java.util.function.Supplier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetriever;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_SOURCE;

/**
 * Catalog of described Camel sources behind a {@link LanguageModelQueryRouter}. The router model is a stub that picks
 * the support articles source.
 */
@ApplicationScoped
public class RoutedContentRetrieverAugmentor implements Supplier<RetrievalAugmentor> {

    @Inject
    CamelContext camelContext;

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .queryRouter(LanguageModelQueryRouter.builder()
                        .chatModel(new KbPickingRouterModel())
                        .retrieverToDescription(Map.of(
                                new CamelContentRetriever(camelContext, "orders", "direct:retriever-orders"),
                                "The customer's orders of the last 30 days, with status",
                                new CamelContentRetriever(camelContext, "kb", "direct:retriever-kb"),
                                "Support articles: delivery delays, returns, refunds"))
                        .build())
                .contentInjector(DefaultContentInjector.builder()
                        .metadataKeysToInclude(List.of(METADATA_SOURCE))
                        .build())
                .build();
    }

    // answers with the option id ("<id>: <description>") of the support articles source
    static class KbPickingRouterModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            UserMessage prompt = (UserMessage) chatRequest.messages().get(chatRequest.messages().size() - 1);
            String id = prompt.singleText().lines()
                    .filter(l -> l.contains("Support articles"))
                    .map(l -> l.substring(0, l.indexOf(':')).trim())
                    .findFirst()
                    .orElse("");
            return ChatResponse.builder().aiMessage(AiMessage.from(id)).build();
        }
    }
}
