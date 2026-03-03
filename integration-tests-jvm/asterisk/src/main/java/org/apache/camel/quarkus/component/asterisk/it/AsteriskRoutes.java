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

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AsteriskRoutes extends RouteBuilder {

    @ConfigProperty(name = "asterisk.host")
    String asteriskHost;

    @ConfigProperty(name = "asterisk.ami.username")
    String asteriskUsername;

    @ConfigProperty(name = "asterisk.ami.password")
    String asteriskPassword;

    @Override
    public void configure() throws Exception {
        // Route with bridgeErrorHandler=true
        // Exceptions in this consumer will be routed through Camel's error handler
        from(String.format("asterisk:bridge-error-handler-route?hostname=%s&username=%s&password=%s&bridgeErrorHandler=true",
                asteriskHost, asteriskUsername, asteriskPassword))
                .routeId("asterisk-bridge-error-handler-true")
                .errorHandler(deadLetterChannel("mock:error-handler"))
                .process(exchange -> {
                    // Simulate processing that might fail
                    String eventName = exchange.getIn().getHeader("CamelAsteriskEventName", String.class);
                    if ("ERROR_EVENT".equals(eventName)) {
                        throw new RuntimeException("Simulated error in consumer");
                    }
                })
                .to("mock:bridge-error-handler-result");

        // Route with bridgeErrorHandler=false (default)
        // Exceptions will be logged but not routed through error handler
        from(String.format(
                "asterisk:no-bridge-error-handler-route?hostname=%s&username=%s&password=%s&bridgeErrorHandler=false",
                asteriskHost, asteriskUsername, asteriskPassword))
                .routeId("asterisk-bridge-error-handler-false")
                .errorHandler(deadLetterChannel("mock:should-not-reach-this"))
                .process(exchange -> {
                    String eventName = exchange.getIn().getHeader("CamelAsteriskEventName", String.class);
                    if ("ERROR_EVENT".equals(eventName)) {
                        throw new RuntimeException("This error should not reach error handler");
                    }
                })
                .to("mock:no-bridge-error-handler-result");
    }
}
