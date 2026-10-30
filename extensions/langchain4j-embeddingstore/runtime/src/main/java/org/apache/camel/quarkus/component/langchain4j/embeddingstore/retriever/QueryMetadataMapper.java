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
package org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever;

import java.util.Map;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.query.Metadata;
import org.apache.camel.Exchange;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.QUERY_CHAT_MEMORY_ID;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.QUERY_PARAM_PREFIX;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.QUERY_USER_MESSAGE;

/**
 * Maps LangChain4j query metadata to exchange properties. Properties, not headers, so that nothing leaks into endpoint
 * calls made by the route.
 */
public final class QueryMetadataMapper {

    private QueryMetadataMapper() {
    }

    public static void toExchange(Metadata metadata, Exchange exchange) {
        if (metadata == null) {
            return;
        }
        ChatMessage chatMessage = metadata.chatMessage();
        if (chatMessage instanceof UserMessage userMessage && userMessage.hasSingleText()) {
            exchange.setProperty(QUERY_USER_MESSAGE, userMessage.singleText());
        }
        if (metadata.chatMemoryId() != null) {
            exchange.setProperty(QUERY_CHAT_MEMORY_ID, metadata.chatMemoryId());
        }
        InvocationParameters parameters = metadata.invocationParameters();
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.asMap().entrySet()) {
                exchange.setProperty(QUERY_PARAM_PREFIX + entry.getKey(), entry.getValue());
            }
        }
    }
}
