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
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Camel routes as LangChain4j content retrievers behind {@code @RegisterAiService}. The echo chat model returns the
 * prompt it receives, so the answer shows which retrieved content was injected.
 */
@QuarkusTest
class ContentRetrieverTest {

    @Test
    void allSourcesInjected() {
        RestAssured.given()
                .queryParam("customerId", "c-42")
                .body("Is my order delayed?")
                .post("/content-retriever/chat")
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("orderId: 1001"),
                        containsString("customer: c-42"),
                        containsString("status: DELAYED"),
                        containsString("KB article matching 'Is my order delayed?'"),
                        containsString("camel_retrieval_source: orders"),
                        containsString("camel_retrieval_source: kb"),
                        containsString("camel_retrieval_document_id: 1001")));
    }

    @Test
    void callerIdentitySelectsData() {
        RestAssured.given()
                .queryParam("customerId", "c-7")
                .body("Is my order delayed?")
                .post("/content-retriever/chat")
                .then()
                .statusCode(200)
                .body(allOf(containsString("customer: c-7"), not(containsString("customer: c-42"))));
    }

    @Test
    void languageModelRouterSelectsSource() {
        RestAssured.given()
                .queryParam("customerId", "c-42")
                .body("Can I get a refund?")
                .post("/content-retriever/routed-chat")
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("KB article matching 'Can I get a refund?'"),
                        containsString("camel_retrieval_source: kb"),
                        not(containsString("orderId: 1001"))));
    }
}
