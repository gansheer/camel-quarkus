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

import dev.langchain4j.invocation.InvocationParameters;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/content-retriever")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class ContentRetrieverResource {

    @Inject
    ContentRetrieverAiService aiService;

    @Inject
    RoutedContentRetrieverAiService routedAiService;

    // customerId stands for a server-side identity (e.g. SecurityIdentity); it never comes from the prompt
    @POST
    @Path("/chat")
    public String chat(@QueryParam("customerId") String customerId, String question) {
        return aiService.chat(question, parameters(customerId));
    }

    @POST
    @Path("/routed-chat")
    public String routedChat(@QueryParam("customerId") String customerId, String question) {
        return routedAiService.chat(question, parameters(customerId));
    }

    private static InvocationParameters parameters(String customerId) {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put("customerId", customerId);
        return parameters;
    }
}
