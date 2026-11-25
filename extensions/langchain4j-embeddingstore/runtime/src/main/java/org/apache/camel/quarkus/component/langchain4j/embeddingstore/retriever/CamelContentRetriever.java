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
 * rejection, a timeout or a route failure follows the {@link #setOnError(OnError)} policy. {@link #setConcurrency(int)}
 * and {@link #setQueueCapacity(int)} size the executor.
 */
public class CamelContentRetriever implements ContentRetriever, CamelContextAware, Service {

    public static final int DEFAULT_CONCURRENCY = 2;
    public static final int DEFAULT_QUEUE_CAPACITY = 64;

    private CamelContext camelContext;
    private final String name;
    private final String endpointUri;
    private String documentIdKey;
    private Duration timeout;
    private OnError onError = OnError.FAIL;
    private int maxResults = -1;
    private int maxChars = -1;
    private int concurrency = DEFAULT_CONCURRENCY;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    // replaced as a whole so retrieve() never sees a template without its endpoint
    private volatile Target target;

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

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Number of route calls this retriever runs in parallel. Calls beyond it wait in the queue; when that is full too,
     * the call is rejected and follows {@code on-error}.
     */
    public CamelContentRetriever setConcurrency(int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1: " + concurrency);
        }
        this.concurrency = concurrency;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** Number of calls that may wait for a free thread; beyond it a call is rejected and follows {@code on-error}. */
    public CamelContentRetriever setQueueCapacity(int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be >= 1: " + queueCapacity);
        }
        this.queueCapacity = queueCapacity;
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
                concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), new ThreadPoolExecutor.AbortPolicy());
        try {
            ProducerTemplate template = getCamelContext().createProducerTemplate();
            // replaces the template's default caller-runs pool, which would run the route on the calling thread once full
            template.setExecutorService(pool);
            target = new Target(template, getCamelContext().getEndpoint(endpointUri));
        } catch (RuntimeException e) {
            pool.shutdownNow();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        Target t = target;
        target = null;
        // stopping the template shuts the executor down and completes the futures of the tasks it dropped
        if (t != null) {
            ServiceHelper.stopService(t.template());
        }
    }

    @Override
    public List<Content> retrieve(Query query) {
        Target t = target;
        if (t == null) {
            throw new IllegalStateException(
                    name + " is not started: register it with CamelContext.addService(Object) to start it");
        }

        Exchange exchange = t.endpoint().createExchange(ExchangePattern.InOut);
        exchange.getMessage().setBody(query.text());
        QueryMetadataMapper.toExchange(query.metadata(), exchange);

        CompletableFuture<Exchange> future;
        try {
            future = t.template().asyncSend(t.endpoint(), exchange);
        } catch (RejectedExecutionException e) {
            return onError.failure(name, "was rejected (executor saturated)", e);
        } catch (IllegalStateException e) {
            // the template stopped between the snapshot above and the send
            return onError.failure(name, "is stopping", e);
        }
        try {
            Exchange answer = timeout == null
                    ? future.get()
                    : future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
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

    private record Target(ProducerTemplate template, Endpoint endpoint) {
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
