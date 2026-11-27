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
package org.apache.camel.quarkus.component.langchain4j.ragbridge.it;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Retrievers declared in configuration and with {@code @Retriever}, referenced by a configured augmentor that has no
 * embedding store. No {@code @QuarkusIntegrationTest} counterpart: the profile changes build-time configuration.
 */
@QuarkusTest
@TestProfile(ConfiguredRetrieverProfile.class)
class ConfiguredRetrieverTest {

    @Test
    void configuredAugmentorRetrievesFromBothRetrievers() {
        RestAssured.given()
                .queryParam("customerId", "c-42")
                .body("Is my order delayed?")
                .post("/content-retriever/configured-chat")
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("customer: c-42"),
                        containsString("camel_retrieval_source: orders-config"),
                        containsString("camel_retrieval_document_id: 1001"),
                        containsString("KB article matching 'Is my order delayed?'"),
                        containsString("camel_retrieval_source: kb-builder")));
    }

    @Test
    void configuredRetrieverIsANamedBean() {
        RestAssured.get("/content-retriever/retriever-bean/orders-config")
                .then()
                .statusCode(200)
                .body(is("CamelContentRetriever[orders-config -> direct:retriever-orders]"));
    }

    @Test
    void builderRetrieverIsANamedBean() {
        RestAssured.get("/content-retriever/retriever-bean/kb-builder")
                .then()
                .statusCode(200)
                .body(is("CamelContentRetriever[kb-builder -> direct:retriever-kb]"));
    }
}
