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

import java.util.List;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Service;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * POC copy of {@code org.apache.camel.component.langchain4j.core.rag.CamelContentRetriever} (Camel 4.24), until
 * Camel Quarkus moves to that Camel version.
 * <p>
 * A LangChain4j {@link ContentRetriever} that delegates retrieval to a Camel endpoint (a route, a Kamelet or a
 * component).
 * <p>
 * The query text is sent as the message body; query metadata is set as exchange properties (see
 * {@link CamelContentRetrieverConstants}). The response body is converted by {@link ContentConverter}.
 */
public class CamelContentRetriever implements ContentRetriever, CamelContextAware, Service {

    private CamelContext camelContext;
    private final String name;
    private final String endpointUri;
    private String documentIdKey;
    private volatile ProducerTemplate producer;

    public CamelContentRetriever(CamelContext camelContext, String name, String endpointUri) {
        this.camelContext = camelContext;
        this.name = ObjectHelper.notNull(name, "name");
        this.endpointUri = ObjectHelper.notNull(endpointUri, "endpointUri");
    }

    public String getName() {
        return name;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public String getDocumentIdKey() {
        return documentIdKey;
    }

    /**
     * Key of the element id when the route returns {@code Map} elements; copied to
     * {@value CamelContentRetrieverConstants#METADATA_DOCUMENT_ID}.
     */
    public CamelContentRetriever setDocumentIdKey(String documentIdKey) {
        this.documentIdKey = documentIdKey;
        return this;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void start() {
        producer();
    }

    @Override
    public synchronized void stop() {
        ServiceHelper.stopService(producer);
        producer = null;
    }

    @Override
    public List<Content> retrieve(Query query) {
        Exchange exchange = producer().request(endpointUri, e -> {
            e.getMessage().setBody(query.text());
            QueryMetadataMapper.toExchange(query.metadata(), e);
        });
        if (exchange.getException() != null) {
            throw CamelExecutionException.wrapCamelExecutionException(exchange, exchange.getException());
        }
        return ContentConverter.toContents(exchange, name, documentIdKey);
    }

    // lazy: the retriever is often created inside a Supplier<RetrievalAugmentor> where nobody calls start()
    private ProducerTemplate producer() {
        ProducerTemplate answer = producer;
        if (answer == null) {
            synchronized (this) {
                answer = producer;
                if (answer == null) {
                    ObjectHelper.notNull(camelContext, "camelContext");
                    answer = camelContext.createProducerTemplate();
                    producer = answer;
                }
            }
        }
        return answer;
    }

    @Override
    public String toString() {
        return "CamelContentRetriever[" + name + " -> " + URISupport.sanitizeUri(endpointUri) + "]";
    }
}
