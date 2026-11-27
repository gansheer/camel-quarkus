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
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

/**
 * The failure and cost policy of each retriever declared in {@link RetrieverBuildTimeConfig}.
 */
@ConfigMapping(prefix = "quarkus.camel.langchain4j.retriever")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface RetrieverRunTimeConfig {

    /**
     * Camel content retrievers by name.
     */
    @WithParentName
    @WithDefaults
    @ConfigDocMapKey("retriever-name")
    Map<String, RetrieverConfig> retrievers();

    interface RetrieverConfig {

        /**
         * How long a retrieval waits for the route, time spent in the queue included.
         *
         * @asciidoclet
         */
        @WithDefault("30s")
        Duration timeout();

        /**
         * What happens when the route fails, is rejected, times out or returns a body that cannot be converted:
         * `fail` fails the AI service call, `empty` logs a warning and contributes no content.
         *
         * @asciidoclet
         */
        @WithDefault("fail")
        OnError onError();

        /**
         * Maximum number of items this retriever contributes; 0 means unlimited.
         *
         * @asciidoclet
         */
        @WithDefault("0")
        int maxResults();

        /**
         * Maximum number of characters this retriever contributes across all items; 0 means unlimited.
         *
         * @asciidoclet
         */
        @WithDefault("0")
        int maxChars();

        /**
         * Number of route calls this retriever runs in parallel.
         *
         * @asciidoclet
         */
        @WithDefault("2")
        int concurrency();

        /**
         * Number of calls that may wait for a free thread; beyond it a call is rejected and follows `on-error`.
         *
         * @asciidoclet
         */
        @WithDefault("64")
        int queueCapacity();

        /**
         * Key of the element id when the route returns `Map` elements; copied to `camel_retrieval_document_id`.
         *
         * @asciidoclet
         */
        Optional<String> documentIdKey();
    }
}
