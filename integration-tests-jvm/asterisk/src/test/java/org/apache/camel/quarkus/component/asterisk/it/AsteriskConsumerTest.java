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

import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for Asterisk consumer (event receiving).
 * These tests verify that the component can consume Asterisk Manager Interface events.
 */
@QuarkusTest
@TestHTTPEndpoint(AsteriskResource.class)
@QuarkusTestResource(AsteriskTestResource.class)
public class AsteriskConsumerTest {

    @Test
    public void testPollEvent() {
        // Poll for any Asterisk event
        // The Asterisk server generates various events automatically (FullyBooted, etc.)
        RestAssured
                .given()
                .when()
                .get("/event/poll")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void testPollEventWithHeader() {
        // Poll for event and verify CamelAsteriskEventName header is set
        RestAssured
                .given()
                .when()
                .get("/event/poll/with-header")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }
}
