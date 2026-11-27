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
package org.apache.camel.quarkus.component.langchain4j.content.retriever.it;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/** Camel retrievers with no embedding store and no Quarkus LangChain4j: declared, injected, wired by hand. */
@QuarkusTest
class Langchain4jContentRetrieverTest {

    @Test
    void configuredRetrieverReadsTheInvocationParameters() {
        RestAssured.given()
                .queryParam("customerId", "c-42")
                .body("Is my order delayed?")
                .post("/content-retriever/retrieve/orders")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].text", allOf(containsString("orderId: 1001"), containsString("customer: c-42")))
                .body("[0].metadata.camel_retrieval_source", is("orders"))
                .body("[0].metadata.camel_retrieval_document_id", is("1001"));
    }

    @Test
    void builderRetrieverAppliesItsSpec() {
        RestAssured.given()
                .body("refund policy")
                .post("/content-retriever/retrieve/kb")
                .then()
                .statusCode(200)
                // the route returns three articles, maxResults(2) keeps two
                .body("", hasSize(2))
                .body("[0].text", is("KB article 1 for 'refund policy'"))
                .body("[0].metadata.camel_retrieval_source", is("kb"));
    }

    @Test
    void failingSourceDegradesToNoContent() {
        RestAssured.given()
                .body("anything")
                .post("/content-retriever/retrieve/broken")
                .then()
                .statusCode(200)
                .body("", empty());
        RestAssured.get("/content-retriever/failures/broken")
                .then()
                .statusCode(200)
                .body("ROUTE_FAILURE", is(1));
    }

    @Test
    void handWiredAugmentorUsesTheInjectedRetrievers() {
        RestAssured.given()
                .queryParam("customerId", "c-7")
                .body("Where is my order?")
                .post("/content-retriever/augment")
                .then()
                .statusCode(200)
                .body(allOf(
                        containsString("Where is my order?"),
                        containsString("customer: c-7"),
                        containsString("camel_retrieval_source: orders"),
                        containsString("camel_retrieval_document_id: 1001"),
                        containsString("KB article 2 for 'Where is my order?'"),
                        containsString("camel_retrieval_source: kb")));
    }

    @Test
    void retrieversAreNamedBeans() {
        RestAssured.get("/content-retriever/bean/orders")
                .then()
                .statusCode(200)
                .body(is("CamelContentRetriever[orders -> direct:orders]"));
        RestAssured.get("/content-retriever/bean/kb")
                .then()
                .statusCode(200)
                .body(is("CamelContentRetriever[kb -> direct:kb]"));
    }
}
