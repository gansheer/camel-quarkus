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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

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
 * Each retriever owns a {@link ProducerTemplate} and a bounded thread pool, both created on start and released on stop:
 * register it with {@link CamelContext#addService(Object)} so the context manages its lifecycle, and configure it
 * before it starts. {@link #retrieve(Query)} runs the exchange on the pool and waits at most
 * {@link #setTimeout(Duration)}; a rejection, a timeout or a route failure follows the {@link #setOnError(OnError)}
 * policy. {@link #setConcurrency(int)} and {@link #setQueueCapacity(int)} size the pool.
 */
public class CamelContentRetriever extends ServiceSupport implements ContentRetriever, CamelContextAware {

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
    // replaced as a whole so retrieve() never sees a template without its endpoint or pool; the volatile write in
    // doStart() also publishes the configuration, which cannot change while started
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
        checkNotStarted();
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
        checkNotStarted();
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
        checkNotStarted();
        this.onError = ObjectHelper.notNull(onError, "onError");
        return this;
    }

    public int getMaxResults() {
        return maxResults;
    }

    /** Maximum number of {@link Content} items this source contributes; negative means unlimited. */
    public CamelContentRetriever setMaxResults(int maxResults) {
        checkNotStarted();
        this.maxResults = positiveOrUnlimited(maxResults, "maxResults");
        return this;
    }

    public int getMaxChars() {
        return maxChars;
    }

    /** Maximum number of characters this source contributes across all items; negative means unlimited. */
    public CamelContentRetriever setMaxChars(int maxChars) {
        checkNotStarted();
        this.maxChars = positiveOrUnlimited(maxChars, "maxChars");
        return this;
    }

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Number of route calls this retriever runs in parallel. Calls beyond it wait in the queue; when that is full too,
     * the call is rejected and follows {@code onError}.
     */
    public CamelContentRetriever setConcurrency(int concurrency) {
        checkNotStarted();
        if (concurrency < 1) {
            throw new IllegalArgumentException("concurrency must be >= 1: " + concurrency);
        }
        this.concurrency = concurrency;
        return this;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    /** Number of calls that may wait for a free thread; beyond it a call is rejected and follows {@code onError}. */
    public CamelContentRetriever setQueueCapacity(int queueCapacity) {
        checkNotStarted();
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
        checkNotStarted();
        this.camelContext = camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, endpointUri);
        ThreadPoolProfile profile = new ThreadPoolProfile(name);
        profile.setPoolSize(concurrency);
        profile.setMaxPoolSize(concurrency);
        profile.setMaxQueueSize(queueCapacity);
        // never caller-runs: a saturated source must not run its route on the calling thread
        profile.setRejectedPolicy(ThreadPoolRejectedPolicy.Abort);
        ExecutorService pool = camelContext.getExecutorServiceManager()
                .newThreadPool(this, "CamelContentRetriever[" + name + "]", profile);
        try {
            target = new Target(camelContext.createProducerTemplate(), endpoint, pool, ConcurrentHashMap.newKeySet());
        } catch (RuntimeException e) {
            camelContext.getExecutorServiceManager().shutdownNow(pool);
            throw e;
        }
    }

    @Override
    protected void doStop() throws Exception {
        Target t = target;
        target = null;
        if (t == null) {
            return;
        }
        camelContext.getExecutorServiceManager().shutdownNow(t.pool());
        // the pool dropped these calls: complete them so their callers follow on-error instead of hanging
        for (RetrievalTask task : t.pending()) {
            task.reject();
        }
        ServiceHelper.stopService(t.template());
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

        RetrievalTask task = new RetrievalTask(t, exchange);
        t.pending().add(task);
        try {
            t.pool().execute(task);
        } catch (RejectedExecutionException e) {
            t.pending().remove(task);
            return onError.failure(name, t.pool().isShutdown() ? "is stopping" : "was rejected (executor saturated)", e);
        }

        CompletableFuture<Exchange> future = task.future;
        try {
            Exchange answer = timeout == null
                    ? future.get()
                    : future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (answer.getException() != null) {
                return onError.failure(name, "failed", answer.getException());
            }
            return ContentConverter.toContents(answer, name, documentIdKey, maxResults, maxChars);
        } catch (TimeoutException e) {
            // a call still queued is skipped; one already running keeps running until the source answers
            future.cancel(false);
            return onError.failure(name, "timed out after " + timeout, e);
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            throw new CamelExecutionException(name + " was interrupted while waiting for the route", null, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return onError.failure(name, cause instanceof RejectedExecutionException ? "was stopped" : "failed", cause);
        }
    }

    private void checkNotStarted() {
        if (isStartingOrStarted()) {
            throw new IllegalStateException(name + " is started: configure it before it starts");
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

    private record Target(ProducerTemplate template, Endpoint endpoint, ExecutorService pool, Set<RetrievalTask> pending) {
    }

    private static final class RetrievalTask implements Runnable {
        private final Target target;
        private final Exchange exchange;
        private final CompletableFuture<Exchange> future = new CompletableFuture<>();
        private final AtomicBoolean claimed = new AtomicBoolean();

        RetrievalTask(Target target, Exchange exchange) {
            this.target = target;
            this.exchange = exchange;
        }

        @Override
        public void run() {
            target.pending().remove(this);
            // the caller gave up (timeout, interrupt) while the call was queued: do not call the source
            if (future.isDone() || !claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                future.complete(target.template().send(target.endpoint(), exchange));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        }

        void reject() {
            target.pending().remove(this);
            if (claimed.compareAndSet(false, true)) {
                future.completeExceptionally(
                        new RejectedExecutionException("the retriever stopped before the call reached the route"));
            }
        }
    }
}
