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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
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
 * <p>
 * Each retriever owns a {@link ProducerTemplate} and a bounded executor, both created in {@link #start()} and stopped
 * in {@link #stop()}: register it with {@link CamelContext#addService(Object)} so the context manages its lifecycle.
 * {@link #retrieve(Query)} sends the exchange asynchronously and waits at most {@link #setTimeout(Duration)}; a
 * rejection or a timeout follows the {@link #setOnError(OnError)} policy.
 */
public class CamelContentRetriever implements ContentRetriever, CamelContextAware, Service {

    private static final int CORE_POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 64;

    private CamelContext camelContext;
    private final String name;
    private final String endpointUri;
    private String documentIdKey;
    private Duration timeout;
    private OnError onError = OnError.FAIL;
    private int maxResults = -1;
    private int maxChars = -1;
    private volatile ProducerTemplate producer;
    private volatile Endpoint endpoint;

    public CamelContentRetriever(CamelContext camelContext, String name, String endpointUri) {
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
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

    public Duration getTimeout() {
        return timeout;
    }

    /**
     * How long {@link #retrieve(Query)} waits for the route. Without a timeout it waits until the route answers.
     */
    public CamelContentRetriever setTimeout(Duration timeout) {
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    public OnError getOnError() {
        return onError;
    }

    /** What happens when the route fails, is rejected or times out. */
    public CamelContentRetriever setOnError(OnError onError) {
        this.onError = ObjectHelper.notNull(onError, "onError");
        return this;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /** Maximum number of {@link Content} items this source contributes; negative means unlimited. */
    public CamelContentRetriever setMaxResults(int maxResults) {
        this.maxResults = positiveOrUnlimited(maxResults, "maxResults");
        return this;
    }

    public int getMaxChars() {
        return maxChars;
    }

    /** Maximum number of characters this source contributes across all items; negative means unlimited. */
    public CamelContentRetriever setMaxChars(int maxChars) {
        this.maxChars = positiveOrUnlimited(maxChars, "maxChars");
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
        ExecutorService pool = new ThreadPoolExecutor(
                CORE_POOL_SIZE, CORE_POOL_SIZE, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), new ThreadPoolExecutor.AbortPolicy());
        try {
            ProducerTemplate template = getCamelContext().createProducerTemplate();
            // replaces the template's default CallerRuns pool, which would run the route on the calling thread once full
            template.setExecutorService(pool);
            endpoint = getCamelContext().getEndpoint(endpointUri);
            producer = template;
        } catch (RuntimeException e) {
            pool.shutdownNow();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        ProducerTemplate template = producer;
        producer = null;
        endpoint = null;
        // stopping the template shuts the executor down and rejects pending sends
        ServiceHelper.stopService(template);
    }

    @Override
    public List<Content> retrieve(Query query) {
        ProducerTemplate template = producer;
        Endpoint target = endpoint;
        if (template == null || target == null) {
            throw new IllegalStateException(
                    name + " is not started: register it with CamelContext.addService(Object) to start it");
        }

        Exchange exchange = target.createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody(query.text());
        QueryMetadataMapper.toExchange(query.metadata(), exchange);

        CompletableFuture<Exchange> future;
        try {
            future = template.asyncSend(target, exchange);
        } catch (RejectedExecutionException e) {
            return onError.failure(name, "was rejected (executor saturated)", e);
        }
        try {
            Exchange answer = timeout == null
                    ? future.get()
                    : future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (answer.getException() != null) {
                return onError.failure(name, "failed", answer.getException());
            }
            return ContentConverter.toContents(answer, name, documentIdKey, maxResults, maxChars);
        } catch (TimeoutException e) {
            // cancelling does not interrupt the route: the work keeps running until the source answers
            future.cancel(false);
            return onError.failure(name, "timed out after " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CamelExecutionException(name + " was interrupted while waiting for the route", null, e);
        } catch (ExecutionException e) {
            return onError.failure(name, "failed", e.getCause() != null ? e.getCause() : e);
        }
    }

    private static int positiveOrUnlimited(int value, String what) {
        if (value >= -1) {
            return value;
        }
        throw new IllegalArgumentException(what + " must be >= 0, or -1 for unlimited: " + value);
    }

    @Override
    public String toString() {
        return "CamelContentRetriever[" + name + " -> " + URISupport.sanitizeUri(endpointUri) + "]";
    }
}
