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
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Path("/asterisk")
@ApplicationScoped
public class AsteriskResource {

    private static final Logger LOG = Logger.getLogger(AsteriskResource.class);
    private static final String COMPONENT_ASTERISK = "asterisk";

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    @ConfigProperty(name = "asterisk.host")
    String asteriskHost;

    @ConfigProperty(name = "asterisk.ami.username")
    String asteriskUsername;

    @ConfigProperty(name = "asterisk.ami.password")
    String asteriskPassword;

    @Path("/load/component/asterisk")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentAsterisk() throws Exception {
        if (context.getComponent(COMPONENT_ASTERISK) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_ASTERISK);
        return Response.status(500, COMPONENT_ASTERISK + " could not be loaded from the Camel context").build();
    }

    @Path("/action/{action}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String executeAction(@PathParam("action") String action, String body) throws Exception {
        LOG.infof("Executing Asterisk action: %s", action);
        String endpoint = String.format("asterisk:asterisk-test?hostname=%s&username=%s&password=%s&action=%s",
                asteriskHost, asteriskUsername, asteriskPassword, action);

        Exchange exchange = producerTemplate.request(endpoint, ex -> {
            if (body != null && !body.isEmpty()) {
                ex.getIn().setBody(body);
            }
        });

        if (exchange.getException() != null) {
            LOG.errorf("Error executing action %s: %s", action, exchange.getException().getMessage());
            throw exchange.getException();
        }

        Object response = exchange.getMessage().getBody();
        return response != null ? response.toString() : "OK";
    }

    @Path("/queue/status/{queueName}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getQueueStatus(@PathParam("queueName") String queueName) throws Exception {
        LOG.infof("Getting status for queue: %s", queueName);
        String endpoint = String.format(
                "asterisk:asterisk-test?hostname=%s&username=%s&password=%s&action=QUEUE_STATUS",
                asteriskHost, asteriskUsername, asteriskPassword);

        Exchange exchange = producerTemplate.request(endpoint, ex -> {
            ex.getIn().setHeader("Queue", queueName);
        });

        if (exchange.getException() != null) {
            LOG.errorf("Error getting queue status: %s", exchange.getException().getMessage());
            throw exchange.getException();
        }

        Object response = exchange.getMessage().getBody();
        return response != null ? response.toString() : "No response";
    }

    @Path("/event/poll")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String pollEvent() throws Exception {
        LOG.info("Polling for Asterisk event");
        String endpoint = String.format("asterisk:asterisk-test?hostname=%s&username=%s&password=%s",
                asteriskHost, asteriskUsername, asteriskPassword);

        Exchange exchange = consumerTemplate.receive(endpoint, 5000);
        if (exchange != null) {
            Object body = exchange.getMessage().getBody();
            return body != null ? body.toString() : "Empty event";
        }
        return "No event received";
    }
}
