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
package org.apache.camel.quarkus.component.langchain4j.content.retriever;

import java.time.Duration;

import org.apache.camel.util.ObjectHelper;

/**
 * A retriever declared in Java rather than in configuration, returned from a {@link Retriever} method. Every
 * property has a configuration twin under {@code quarkus.camel.langchain4j.retriever.<name>}, and both paths share
 * the same runtime.
 */
public final class RetrieverSpec {

    private final String uri;
    private String documentIdKey;
    private Duration timeout = CamelContentRetriever.DEFAULT_TIMEOUT;
    private OnError onError = OnError.FAIL;
    private int maxResults;
    private int maxChars;
    private int concurrency = CamelContentRetriever.DEFAULT_CONCURRENCY;
    private int queueCapacity = CamelContentRetriever.DEFAULT_QUEUE_CAPACITY;

    private RetrieverSpec(String uri) {
        this.uri = uri;
    }

    /** The endpoint the retriever sends the query to: a route, a Kamelet or a component; the twin of {@code uri}. */
    public static RetrieverSpec endpoint(String uri) {
        if (ObjectHelper.isEmpty(uri)) {
            throw new IllegalArgumentException("uri must not be empty");
        }
        return new RetrieverSpec(uri);
    }

    public RetrieverSpec documentIdKey(String documentIdKey) {
        this.documentIdKey = documentIdKey;
        return this;
    }

    public RetrieverSpec timeout(Duration timeout) {
        // the same rule the configuration path is held to: a finite, positive timeout
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive (got " + timeout + ")");
        }
        this.timeout = timeout;
        return this;
    }

    public RetrieverSpec onError(OnError onError) {
        this.onError = ObjectHelper.notNull(onError, "onError");
        return this;
    }

    /** 0, the default, means unlimited. */
    public RetrieverSpec maxResults(int maxResults) {
        this.maxResults = maxResults;
        return this;
    }

    /** 0, the default, means unlimited. */
    public RetrieverSpec maxChars(int maxChars) {
        this.maxChars = maxChars;
        return this;
    }

    public RetrieverSpec concurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    public RetrieverSpec queueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
        return this;
    }

    String uri() {
        return uri;
    }

    // the retriever validates every value, so both paths reject the same ones with the same message
    void configure(CamelContentRetriever retriever) {
        retriever.setDocumentIdKey(documentIdKey)
                .setTimeout(timeout)
                .setOnError(onError)
                .setMaxResults(maxResults)
                .setMaxChars(maxChars)
                .setConcurrency(concurrency)
                .setQueueCapacity(queueCapacity);
    }
}
