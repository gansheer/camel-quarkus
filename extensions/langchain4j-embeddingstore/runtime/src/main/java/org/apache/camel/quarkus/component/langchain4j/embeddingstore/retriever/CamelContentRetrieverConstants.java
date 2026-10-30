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

/**
 * Exchange property names set by {@link CamelContentRetriever} and segment metadata keys it writes.
 */
public final class CamelContentRetrieverConstants {

    /** Exchange property: text of the original user message. */
    public static final String QUERY_USER_MESSAGE = "langchain4j.query.userMessage";
    /** Exchange property: chat memory id of the AI Service invocation. */
    public static final String QUERY_CHAT_MEMORY_ID = "langchain4j.query.chatMemoryId";
    /** Exchange property prefix: one property per invocation parameter. */
    public static final String QUERY_PARAM_PREFIX = "langchain4j.query.param.";

    /** Segment metadata key: name of the source that produced the content. */
    public static final String METADATA_SOURCE = "camel_retrieval_source";
    /** Segment metadata key: id of the row or document the content comes from. */
    public static final String METADATA_DOCUMENT_ID = "camel_retrieval_document_id";

    private CamelContentRetrieverConstants() {
    }
}
