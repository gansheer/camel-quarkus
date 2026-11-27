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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Camel content retriever in Java, the twin of the {@code quarkus.camel.langchain4j.retriever.*}
 * configuration:
 *
 * <pre>
 * &#64;Retriever("orders")
 * RetrieverSpec orders() {
 *     return RetrieverSpec.endpoint("direct:orders")
 *             .timeout(Duration.ofSeconds(2))
 *             .onError(OnError.EMPTY);
 * }
 * </pre>
 *
 * The retriever is a {@code @Named("orders")} {@link dev.langchain4j.rag.content.retriever.ContentRetriever} bean,
 * started and stopped with the Camel context, which a RAG bridge augmentor references by name. The annotated method
 * must be declared on a CDI bean, return {@link RetrieverSpec}, take no parameters and be side-effect free: it
 * runs exactly once, at startup. Retriever names must not collide with configuration-declared retrievers.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Retriever {

    /** The retriever name: its bean name and its {@code camel_retrieval_source}. */
    String value();
}
