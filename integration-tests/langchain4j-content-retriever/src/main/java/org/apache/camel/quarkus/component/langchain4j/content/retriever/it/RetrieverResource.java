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

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import io.quarkus.arc.Arc;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelContentRetriever;
import org.apache.camel.quarkus.component.langchain4j.content.retriever.CamelContentRetrieverConstants;

@Path("/content-retriever")
@ApplicationScoped
public class RetrieverResource {

    @Inject
    @Named("orders")
    ContentRetriever orders;

    @Inject
    @Named("kb")
    ContentRetriever kb;

    // built once from the injected beans, never per request
    private RetrievalAugmentor augmentor;

    @PostConstruct
    void buildAugmentor() {
        augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(orders, kb))
                .contentInjector(DefaultContentInjector.builder()
                        .metadataKeysToInclude(List.of(CamelContentRetrieverConstants.METADATA_SOURCE,
                                CamelContentRetrieverConstants.METADATA_DOCUMENT_ID))
                        .build())
                .build();
    }

    public record Item(String text, Map<String, Object> metadata) {
    }

    @POST
    @Path("/retrieve/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Item> retrieve(@PathParam("name") String name, @QueryParam("customerId") String customerId,
            String text) {
        UserMessage userMessage = UserMessage.from(text);
        return retriever(name).retrieve(Query.from(text, metadata(userMessage, customerId))).stream()
                .map(c -> new Item(c.textSegment().text(), c.textSegment().metadata().toMap()))
                .toList();
    }

    @POST
    @Path("/augment")
    @Produces(MediaType.TEXT_PLAIN)
    public String augment(@QueryParam("customerId") String customerId, String text) {
        UserMessage userMessage = UserMessage.from(text);
        UserMessage augmented = (UserMessage) augmentor
                .augment(new AugmentationRequest(userMessage, metadata(userMessage, customerId)))
                .chatMessage();
        return augmented.singleText();
    }

    @GET
    @Path("/bean/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String bean(@PathParam("name") String name) {
        return retriever(name).toString();
    }

    @GET
    @Path("/failures/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Long> failures(@PathParam("name") String name) {
        return retriever(name).getFailureCounts();
    }

    private static CamelContentRetriever retriever(String name) {
        return Arc.container().instance(CamelContentRetriever.class, NamedLiteral.of(name)).get();
    }

    private static Metadata metadata(UserMessage userMessage, String customerId) {
        InvocationContext.Builder context = InvocationContext.builder().userMessage(userMessage).chatMemoryId("default");
        if (customerId != null) {
            context.invocationParameters(InvocationParameters.from("customerId", customerId));
        }
        return Metadata.builder().chatMessage(userMessage).invocationContext(context.build()).build();
    }
}
