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
package org.apache.camel.quarkus.component.mina.sftp.it;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.support.certificate.TestCertificates;
import org.apache.camel.quarkus.test.support.sftp.SftpTestResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * HIGH PRIORITY #3: Route-based tests with MockEndpoint
 *
 * Tests Camel routes consuming from and producing to SFTP endpoints,
 * using MockEndpoint for assertions (classic Camel testing pattern).
 */
@TestCertificates(certificates = {
        @Certificate(name = "ftp", formats = {
                Format.PEM }, password = "password"),
        @Certificate(name = "ftp", formats = {
                Format.PKCS12 }, password = "password") })
@QuarkusTest
@QuarkusTestResource(SftpTestResource.class)
class MinaSftpRouteTest {

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    @AfterEach
    public void cleanupRoutes() throws Exception {
        // Stop and remove dynamic routes after each test
        context.getRouteController().getControlledRoutes().forEach(route -> {
            try {
                if (route.getRouteId() != null && route.getRouteId().startsWith("test-")) {
                    context.getRouteController().stopRoute(route.getRouteId());
                    context.removeRoute(route.getRouteId());
                }
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    @Test
    public void testSftpConsumerRoute() throws Exception {
        // First upload a file via SFTP
        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "Route-based consumer test",
                Exchange.FILE_NAME, "route-test.txt");

        // Add a route that consumes from SFTP
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&delay=1000&delete=true&initialDelay=100")
                        .routeId("test-sftp-consumer")
                        .to("mock:sftp-result");
            }
        });

        // Get mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:sftp-result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Route-based consumer test");
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "route-test.txt");

        // Wait for assertion
        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Verify message content
        Exchange exchange = mock.getExchanges().get(0);
        assertEquals("route-test.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));
        assertEquals("Route-based consumer test", exchange.getIn().getBody(String.class));
    }

    @Test
    public void testSftpProducerRoute() throws Exception {
        // Add a route that produces to SFTP
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sftp-upload")
                        .routeId("test-sftp-producer")
                        .to("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin")
                        .to("mock:upload-complete");
            }
        });

        // Get mock endpoint
        MockEndpoint mock = context.getEndpoint("mock:upload-complete", MockEndpoint.class);
        mock.expectedMessageCount(1);

        // Send message through route (use sendBodyAndHeaders for multiple headers)
        producerTemplate.sendBodyAndHeaders(
                "direct:sftp-upload",
                "Producer route test",
                Map.of(Exchange.FILE_NAME, "producer-route-test.txt"));

        // Wait for assertion
        mock.await(3, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Verify file was uploaded by downloading it
        String downloaded = consumerTemplate.receiveBody(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&fileName=producer-route-test.txt&delete=true",
                5000,
                String.class);
        assertNotNull(downloaded);
        assertEquals("Producer route test", downloaded);
    }

    @Test
    public void testSftpRouteWithTransformation() throws Exception {
        // Add a route that transforms content
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:transform-upload")
                        .routeId("test-sftp-transform")
                        .transform(body().append(" - TRANSFORMED"))
                        .to("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin")
                        .to("mock:transform-complete");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:transform-complete", MockEndpoint.class);
        mock.expectedMessageCount(1);

        // Send message
        producerTemplate.sendBodyAndHeaders(
                "direct:transform-upload",
                "Original Content",
                Map.of(Exchange.FILE_NAME, "transformed.txt"));

        mock.await(3, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Verify transformation was applied
        String downloaded = consumerTemplate.receiveBody(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&fileName=transformed.txt&delete=true",
                5000,
                String.class);
        assertNotNull(downloaded);
        assertEquals("Original Content - TRANSFORMED", downloaded);
    }

    @Test
    public void testSftpRouteWithFilter() throws Exception {
        // Create files first - some matching, some not
        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "Important file 1",
                Exchange.FILE_NAME, "important-1.txt");

        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "Not important",
                Exchange.FILE_NAME, "regular-file.txt");

        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "Important file 2",
                Exchange.FILE_NAME, "important-2.txt");

        // Add a route that filters files by name pattern
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&antInclude=important-*.txt&delay=1000&delete=true&initialDelay=100")
                        .routeId("test-sftp-filter")
                        .to("mock:filtered-result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:filtered-result", MockEndpoint.class);
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder("Important file 1", "Important file 2");

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Clean up the non-matching file
        consumerTemplate.receiveBody(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&fileName=regular-file.txt&delete=true",
                5000, String.class);
    }

    @Test
    public void testSftpRouteWithMove() throws Exception {
        // Create file first
        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "Move test content",
                Exchange.FILE_NAME, "move-test.txt");

        // Add a route that moves processed files
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&delay=1000&move=processed&initialDelay=100")
                        .routeId("test-sftp-move")
                        .to("mock:move-result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:move-result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Move test content");

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Verify file was moved to processed directory
        String movedContent = producerTemplate.requestBody(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp/processed"
                        + "?password=admin&fileName=move-test.txt&delete=true",
                null, String.class);
        assertNotNull(movedContent);
        assertEquals("Move test content", movedContent);
    }

    @Test
    public void testSftpRouteWithPrivateKey() throws Exception {
        // Add a route using private key authentication
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:privkey-upload")
                        .routeId("test-sftp-privkey")
                        .to("mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                                + "?privateKeyFile=src/test/resources/ssh/test-key-rsa")
                        .to("mock:privkey-complete");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:privkey-complete", MockEndpoint.class);
        mock.expectedMessageCount(1);

        // Send message
        producerTemplate.sendBodyAndHeaders(
                "direct:privkey-upload",
                "Private key route test",
                Map.of(Exchange.FILE_NAME, "privkey-route-test.txt"));

        mock.await(3, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        // Verify upload succeeded
        String downloaded = consumerTemplate.receiveBody(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                        + "?password=admin&fileName=privkey-route-test.txt&delete=true",
                5000, String.class);
        assertNotNull(downloaded);
        assertEquals("Private key route test", downloaded);
    }

    @Test
    public void testSftpRoutePollEnrich() throws Exception {
        // Create file first
        producerTemplate.sendBodyAndHeader(
                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp?password=admin",
                "PollEnrich content",
                Exchange.FILE_NAME, "pollenrich-test.txt");

        // Test pollEnrich pattern (pull from SFTP on-demand)
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:poll-trigger")
                        .routeId("test-sftp-pollenrich")
                        .pollEnrich(
                                "mina-sftp://admin@localhost:{{camel.sftp.test-port}}/sftp"
                                        + "?password=admin&fileName=pollenrich-test.txt&delete=true",
                                5000)
                        .to("mock:pollenrich-result");
            }
        });

        MockEndpoint mock = context.getEndpoint("mock:pollenrich-result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("PollEnrich content");

        // Trigger pollEnrich
        producerTemplate.sendBody("direct:poll-trigger", "TRIGGER");

        mock.await(6, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }
}
