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
 * Integration tests for Asterisk actions: SIP_PEERS and EXTENSION_STATE.
 */
@QuarkusTest
@TestHTTPEndpoint(AsteriskResource.class)
@QuarkusTestResource(AsteriskTestResource.class)
public class AsteriskActionsTest {

    @Test
    public void testSipPeers() {
        RestAssured
                .given()
                .when()
                .get("/sip/peers")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void testSipPeersViaAction() {
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/action/SIP_PEERS")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void testExtensionState() {
        // Test with a typical extension and context
        RestAssured
                .given()
                .when()
                .get("/extension/state/1000/default")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void testActionViaHeader() {
        // Test executing QUEUE_STATUS via CamelAsteriskAction header instead of query param
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/action/header/QUEUE_STATUS")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }

    @Test
    public void testSipPeersViaHeader() {
        // Test SIP_PEERS action via header
        RestAssured
                .given()
                .contentType("text/plain")
                .body("")
                .when()
                .post("/action/header/SIP_PEERS")
                .then()
                .statusCode(200)
                .body(notNullValue());
    }
}
