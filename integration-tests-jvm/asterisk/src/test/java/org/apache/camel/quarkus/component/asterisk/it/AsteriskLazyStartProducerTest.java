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
package org.apache.camel.quarkus.component.asterisk.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;

/**
 * Integration tests for Asterisk lazyStartProducer configuration.
 * Tests verify that producer initialization is deferred until first message when lazyStartProducer=true,
 * and that producers fail immediately on invalid configuration when lazyStartProducer=false.
 */
@QuarkusTest
@TestHTTPEndpoint(AsteriskResource.class)
@QuarkusTestResource(AsteriskTestResource.class)
public class AsteriskLazyStartProducerTest {

    @Test
    public void testLazyProducerWithValidConnection() {
        // lazyStartProducer=true should work fine with valid connection
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/lazy/producer/valid")
                .then()
                .statusCode(200)
                .body(containsString("succeeded"));
    }

    @Test
    public void testLazyProducerWithInvalidConnection() {
        // lazyStartProducer=true should defer initialization
        // Endpoint creation succeeds, but sending still fails (returns 500 from the endpoint exception)
        // The key is that the route can start even with invalid config
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/lazy/producer/invalid")
                .then()
                .statusCode(500);
    }

    @Test
    public void testNonLazyProducerWithInvalidConnection() {
        // lazyStartProducer=false should fail immediately on invalid connection
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/non-lazy/producer/invalid")
                .then()
                .statusCode(200)
                .body(containsString("failed immediately as expected"));
    }
}
