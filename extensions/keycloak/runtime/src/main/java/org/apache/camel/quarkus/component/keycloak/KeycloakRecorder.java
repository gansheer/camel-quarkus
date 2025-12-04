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
package org.apache.camel.quarkus.component.keycloak;

import javax.net.ssl.SSLContext;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.resteasy.common.runtime.jackson.QuarkusJacksonSerializer;
import io.quarkus.runtime.annotations.Recorder;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.spi.ResteasyClientProvider;

@Recorder
public class KeycloakRecorder {

    public void avoidRuntimeInitIssueInClientBuilderWrapper() {
        // We set our provider at runtime, it is not used before that
        // However org.keycloak.admin.client.Keycloak.CLIENT_PROVIDER is initialized during
        // static init with org.keycloak.admin.client.ClientBuilderWrapper that is not compatible with native mode
        Keycloak.setClientProvider(null);
    }

    public void setNativeModeClientProvider() {
        Keycloak.setClientProvider(new ResteasyClientProvider() {
            @Override
            public Client newRestEasyClient(Object customJacksonProvider, SSLContext sslContext, boolean disableTrustManager) {
                var builder = new ResteasyClientBuilderImpl();
                builder.connectionPoolSize(10);

                if (sslContext != null) {
                    builder.sslContext(sslContext);
                }
                if (disableTrustManager) {
                    builder.disableTrustManager();
                }

                // Use QuarkusJacksonSerializer that works in native mode
                // This ensures we don't customize managed (shared) ObjectMapper available in the CDI container
                builder.register(new AppJsonQuarkusJacksonSerializer(), 100);

                return builder.build();
            }

            @Override
            public <R> R targetProxy(WebTarget webTarget, Class<R> aClass) {
                return (ResteasyWebTarget.class.cast(webTarget)).proxy(aClass);
            }
        });
    }

    // Makes media type more specific which ensures that it will be used first
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    static final class AppJsonQuarkusJacksonSerializer extends QuarkusJacksonSerializer {

        private final ObjectMapper objectMapper;

        private AppJsonQuarkusJacksonSerializer() {
            this.objectMapper = new ObjectMapper();
            // Same like JSONSerialization class. Makes it possible to use admin-client against older
            // versions of Keycloak server where the properties on representations might be different
            this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            // The client must work with the newer versions of Keycloak server, which might contain the JSON fields
            // not yet known by the client. So unknown fields will be ignored.
            this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        @Override
        public ObjectMapper locateMapper(Class<?> type, MediaType mediaType) {
            return objectMapper;
        }
    }
}
