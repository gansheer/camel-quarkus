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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.core.LangChain4j;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.INGEST_METADATA_DOCUMENT_ID;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_SOURCE;

/**
 * Converts the message body returned by a retrieval route to {@code List<Content>}, one {@link Content} per element:
 * <ul>
 * <li>{@code null}, empty collection: empty list</li>
 * <li>{@link Content}, {@link TextSegment}, {@link String}: used as-is</li>
 * <li>{@link EmbeddingMatch}: its embedded item, with the score and the embedding id as {@link ContentMetadata}</li>
 * <li>{@link Map}: one {@code key: value} line per entry</li>
 * <li>anything else: converted to {@link String} with the Camel type converter</li>
 * </ul>
 * A {@link Collection}, an object array or an {@link Iterator} body is split into its elements; any other body is a
 * single element. Blank elements are skipped. Every segment gets
 * {@value CamelContentRetrieverConstants#METADATA_SOURCE} and the response headers prefixed with
 * {@value LangChain4j#METADATA_PREFIX}. The reserved {@code camel_retrieval_*} and {@code camel_ingest_*} keys never
 * come from response headers. A segment may set {@value CamelContentRetrieverConstants#METADATA_DOCUMENT_ID} itself;
 * otherwise its {@value CamelContentRetrieverConstants#INGEST_METADATA_DOCUMENT_ID}, written by
 * camel-langchain4j-ingest, is copied to it.
 * <p>
 * {@code maxResults} and {@code maxChars} are applied after conversion: the first keeps the first N items, the second
 * truncates the combined text. {@code 0} means unlimited, as {@code maxDocumentSize} in camel-langchain4j-ingest.
 */
final class ContentConverter {

    private static final String RESERVED_RETRIEVAL_PREFIX = "camel_retrieval_";
    private static final String RESERVED_INGEST_PREFIX = "camel_ingest_";

    private ContentConverter() {
    }

    static List<Content> toContents(
            Exchange exchange, String sourceName, String documentIdKey, int maxResults, int maxChars) {
        Collection<?> items = items(exchange.getMessage().getBody());
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> headerMetadata = headerMetadata(exchange);
        List<Content> contents = new ArrayList<>(items.size());
        for (Object item : items) {
            Content content = toContent(exchange, item, sourceName, documentIdKey, headerMetadata);
            if (content != null) {
                contents.add(content);
            }
        }
        if (maxResults > 0 && contents.size() > maxResults) {
            contents = new ArrayList<>(contents.subList(0, maxResults));
        }
        if (maxChars > 0) {
            contents = truncate(contents, maxChars);
        }
        return contents;
    }

    private static Collection<?> items(Object body) {
        if (body == null) {
            return Collections.emptyList();
        }
        if (body instanceof Collection<?> collection) {
            return collection;
        }
        if (body instanceof Object[] array) {
            return Arrays.asList(array);
        }
        if (body instanceof Iterator<?> iterator) {
            List<Object> answer = new ArrayList<>();
            iterator.forEachRemaining(answer::add);
            return answer;
        }
        return Collections.singletonList(body);
    }

    private static Content toContent(
            Exchange exchange, Object item, String sourceName, String documentIdKey, Map<String, Object> headerMetadata) {
        if (item == null) {
            return null;
        }
        Map<ContentMetadata, Object> contentMetadata = null;
        if (item instanceof EmbeddingMatch<?> match) {
            // the default output of an embedding store search: the vector itself never reaches the prompt
            contentMetadata = new EnumMap<>(ContentMetadata.class);
            if (match.score() != null) {
                contentMetadata.put(ContentMetadata.SCORE, match.score());
            }
            if (match.embeddingId() != null) {
                contentMetadata.put(ContentMetadata.EMBEDDING_ID, match.embeddingId());
            }
            item = match.embedded();
            if (item == null) {
                return null;
            }
        }
        TextSegment segment;
        Object documentId = null;
        if (item instanceof Content content) {
            contentMetadata = content.metadata();
            segment = content.textSegment();
        } else if (item instanceof TextSegment textSegment) {
            segment = textSegment;
        } else if (item instanceof Map<?, ?> map) {
            if (documentIdKey != null) {
                documentId = map.get(documentIdKey);
            }
            segment = textSegment(toText(map), null);
        } else {
            String text = item instanceof String s
                    ? s : exchange.getContext().getTypeConverter().convertTo(String.class, exchange, item);
            segment = textSegment(text, null);
        }
        if (segment == null) {
            return null;
        }

        Map<String, Object> metadata = new HashMap<>(headerMetadata);
        metadata.putAll(segment.metadata().toMap());
        // the source is always ours; a segment cannot spoof it
        metadata.put(METADATA_SOURCE, sourceName);
        if (documentId != null) {
            metadata.put(METADATA_DOCUMENT_ID, toMetadataValue(documentId));
        } else if (!metadata.containsKey(METADATA_DOCUMENT_ID) && metadata.containsKey(INGEST_METADATA_DOCUMENT_ID)) {
            // an ingested segment: cite it by its ingest id. Only segment metadata can hold it, headers are filtered
            metadata.put(METADATA_DOCUMENT_ID, metadata.get(INGEST_METADATA_DOCUMENT_ID));
        }
        TextSegment enriched = TextSegment.from(segment.text(), Metadata.from(metadata));
        return contentMetadata != null ? Content.from(enriched, contentMetadata) : Content.from(enriched);
    }

    private static TextSegment textSegment(String text, Metadata metadata) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return metadata != null ? TextSegment.from(text, metadata) : TextSegment.from(text);
    }

    private static List<Content> truncate(List<Content> contents, int maxChars) {
        List<Content> answer = new ArrayList<>(contents.size());
        int remaining = maxChars;
        for (Content content : contents) {
            if (remaining <= 0) {
                break;
            }
            String text = content.textSegment().text();
            if (text.length() <= remaining) {
                answer.add(content);
                remaining -= text.length();
            } else {
                // never split a surrogate pair
                int end = Character.isHighSurrogate(text.charAt(remaining - 1)) ? remaining - 1 : remaining;
                String truncated = text.substring(0, end);
                if (!truncated.isBlank()) {
                    TextSegment segment = content.textSegment();
                    answer.add(Content.from(TextSegment.from(truncated, segment.metadata()), content.metadata()));
                }
                remaining = 0;
            }
        }
        return answer;
    }

    static String toText(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, Object> headerMetadata(Exchange exchange) {
        Map<String, Object> metadata = new HashMap<>();
        for (Map.Entry<String, Object> header : exchange.getMessage().getHeaders().entrySet()) {
            if (header.getKey().startsWith(LangChain4j.METADATA_PREFIX) && header.getValue() != null) {
                String key = header.getKey().substring(LangChain4j.METADATA_PREFIX_LEN);
                // LangChain4j metadata rejects a blank key
                if (key.isBlank()) {
                    continue;
                }
                // the reserved keys are always ours: a response header cannot spoof them. Segment metadata may set
                // the document id on purpose (a route mapping its own id), so only headers are filtered.
                if (key.startsWith(RESERVED_RETRIEVAL_PREFIX) || key.startsWith(RESERVED_INGEST_PREFIX)) {
                    continue;
                }
                metadata.put(key, toMetadataValue(header.getValue()));
            }
        }
        return metadata;
    }

    // LangChain4j Metadata only accepts String, UUID, Integer, Long, Float, Double
    private static Object toMetadataValue(Object value) {
        if (value instanceof String || value instanceof UUID || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) {
            return value;
        }
        return String.valueOf(value);
    }
}
