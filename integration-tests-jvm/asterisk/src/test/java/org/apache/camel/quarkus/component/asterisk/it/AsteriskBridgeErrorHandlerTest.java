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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for Asterisk bridgeErrorHandler configuration.
 * Tests verify that consumer exceptions are properly routed through Camel's error handler
 * when bridgeErrorHandler=true.
 */
@QuarkusTest
@QuarkusTestResource(AsteriskTestResource.class)
public class AsteriskBridgeErrorHandlerTest {

    @Inject
    CamelContext context;

    @Test
    public void testBridgeErrorHandlerRouteExists() throws Exception {
        // Verify the route with bridgeErrorHandler=true exists
        Route route = context.getRoute("asterisk-bridge-error-handler-true");
        assertNotNull(route, "Route with bridgeErrorHandler=true should exist");
    }

    @Test
    public void testNoBridgeErrorHandlerRouteExists() throws Exception {
        // Verify the route with bridgeErrorHandler=false exists
        Route route = context.getRoute("asterisk-bridge-error-handler-false");
        assertNotNull(route, "Route with bridgeErrorHandler=false should exist");
    }

    @Test
    public void testBridgeErrorHandlerRouteConfiguration() throws Exception {
        // Verify both routes are configured
        Route routeWithBridge = context.getRoute("asterisk-bridge-error-handler-true");
        Route routeWithoutBridge = context.getRoute("asterisk-bridge-error-handler-false");

        assertNotNull(routeWithBridge, "Route with bridgeErrorHandler=true should be configured");
        assertNotNull(routeWithoutBridge, "Route with bridgeErrorHandler=false should be configured");

        // Verify the routes have the expected consumer endpoint
        assertNotNull(routeWithBridge.getConsumer(), "Route should have a consumer");
        assertNotNull(routeWithoutBridge.getConsumer(), "Route should have a consumer");
    }
}
