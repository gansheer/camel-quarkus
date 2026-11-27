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

import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

/**
 * The retrievers and the endpoint each one calls. The endpoint is fixed at build time: a URI overridable at runtime
 * would be an arbitrary component invocation. The tuning is runtime configuration, see
 * {@link RetrieverRunTimeConfig}.
 */
@ConfigMapping(prefix = "quarkus.camel.langchain4j.retriever")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RetrieverBuildTimeConfig {

    /**
     * Camel content retrievers by name. Each one is a `@Named("<name>")` `ContentRetriever` bean, started and stopped
     * with the Camel context, which a RAG bridge augmentor references with
     * `quarkus.camel.langchain4j.rag.augmentors.<augmentor>.retrievers.<name>.description`.
     *
     * @asciidoclet
     */
    @WithParentName
    @ConfigDocMapKey("retriever-name")
    Map<String, RetrieverConfig> retrievers();

    interface RetrieverConfig {

        /**
         * The endpoint the retriever sends the query to: a route (`direct:orders`), a Kamelet or a component.
         * Property placeholders are resolved at startup.
         *
         * @asciidoclet
         */
        String uri();
    }
}
