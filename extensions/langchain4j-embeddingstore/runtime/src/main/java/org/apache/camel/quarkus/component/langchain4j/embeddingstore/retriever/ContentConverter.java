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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import org.apache.camel.Exchange;
import org.apache.camel.component.langchain4j.core.LangChain4j;

import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.quarkus.component.langchain4j.embeddingstore.retriever.CamelContentRetrieverConstants.METADATA_SOURCE;

/**
 * Converts the message body returned by a retrieval route to {@code List<Content>}, one {@link Content} per element:
 * <ul>
 * <li>{@code null}, empty collection: empty list</li>
 * <li>{@link Content}, {@link TextSegment}, {@link String}: used as-is</li>
 * <li>{@link Map}: one {@code key: value} line per entry</li>
 * <li>anything else: converted to {@link String} with the Camel type converter</li>
 * </ul>
 * A non-collection body is treated as a single element. Blank elements are skipped. Every segment gets
 * {@value CamelContentRetrieverConstants#METADATA_SOURCE} and the response headers prefixed with
 * {@value LangChain4j#METADATA_PREFIX}.
 */
public final class ContentConverter {

    private ContentConverter() {
    }

    public static List<Content> toContents(Exchange exchange, String sourceName, String documentIdKey) {
        Object body = exchange.getMessage().getBody();
        if (body == null) {
            return Collections.emptyList();
        }
        Collection<?> items = body instanceof Collection<?> collection ? collection : List.of(body);
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
        return contents;
    }

    private static Content toContent(
            Exchange exchange, Object item, String sourceName, String documentIdKey, Map<String, Object> headerMetadata) {
        if (item == null) {
            return null;
        }
        TextSegment segment;
        Content original = null;
        Object documentId = null;
        if (item instanceof Content content) {
            original = content;
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
        metadata.put(METADATA_SOURCE, sourceName);
        if (documentId != null) {
            metadata.put(METADATA_DOCUMENT_ID, toMetadataValue(documentId));
        }
        TextSegment enriched = TextSegment.from(segment.text(), Metadata.from(metadata));
        return original != null ? Content.from(enriched, original.metadata()) : Content.from(enriched);
    }

    private static TextSegment textSegment(String text, Metadata metadata) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return metadata != null ? TextSegment.from(text, metadata) : TextSegment.from(text);
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
                metadata.put(header.getKey().substring(LangChain4j.METADATA_PREFIX_LEN), toMetadataValue(header.getValue()));
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
