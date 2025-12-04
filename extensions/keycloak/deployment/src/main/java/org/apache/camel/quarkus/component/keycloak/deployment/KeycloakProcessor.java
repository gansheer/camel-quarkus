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
package org.apache.camel.quarkus.component.keycloak.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import org.apache.camel.quarkus.component.keycloak.KeycloakRecorder;

class KeycloakProcessor {

    private static final String FEATURE = "camel-keycloak";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerKeycloakArchives(BuildProducer<AdditionalApplicationArchiveMarkerBuildItem> archiveMarkers) {
        // Index Keycloak admin client and representations packages for automatic reflection registration
        // This follows the same approach as quarkus-keycloak-admin-client extension
        archiveMarkers.produce(new AdditionalApplicationArchiveMarkerBuildItem("org/keycloak/admin/client"));
        archiveMarkers.produce(new AdditionalApplicationArchiveMarkerBuildItem("org/keycloak/representations"));
    }

    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        // Register RESTEasy client classes for reflection
        // The Keycloak admin client uses RESTEasy client which instantiates classes via reflection
        // (Apache HTTP Client classes are handled by camel-quarkus-support-httpclient dependency)
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl",
                "org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration",
                "org.jboss.resteasy.client.jaxrs.internal.proxy.ProxyBuilderImpl",
                "org.jboss.resteasy.client.jaxrs.ResteasyClient")
                .constructors()
                .methods()
                .fields()
                .build());

        // Register Keycloak admin client provider and Jackson classes for reflection
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.keycloak.admin.client.JacksonProvider",
                "org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider",
                "org.keycloak.json.StringListMapDeserializer",
                "org.keycloak.json.StringOrArrayDeserializer",
                "org.keycloak.json.StringOrArraySerializer",
                "org.keycloak.common.util.MultivaluedHashMap")
                .constructors()
                .methods()
                .build());

        // Register our custom Jackson serializer for native mode
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.apache.camel.quarkus.component.keycloak.KeycloakRecorder$AppJsonQuarkusJacksonSerializer")
                .constructors()
                .methods()
                .fields()
                .build());

        // Register Keycloak representation classes for serialization
        // Note: Archive markers help with indexing but Keycloak JARs don't have Jandex indexes
        // so we need to explicitly register the main representation classes
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.keycloak.representations.idm.RealmRepresentation",
                "org.keycloak.representations.idm.UserRepresentation",
                "org.keycloak.representations.idm.RoleRepresentation",
                "org.keycloak.representations.idm.CredentialRepresentation",
                "org.keycloak.representations.idm.ClientRepresentation",
                "org.keycloak.representations.idm.RoleRepresentation$Composites",
                "org.keycloak.representations.idm.RealmEventsConfigRepresentation",
                "org.keycloak.representations.idm.AdminEventRepresentation",
                "org.keycloak.representations.idm.EventRepresentation",
                "org.keycloak.representations.idm.AuthDetailsRepresentation",
                "org.keycloak.representations.idm.ErrorRepresentation")
                .constructors()
                .methods()
                .fields()
                .build());
    }

    @BuildStep
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitialized) {
        // Initialize Apache HTTP Async Client engine at runtime instead of build-time
        // This is an optional RESTEasy engine that requires httpcore-nio which is not on the classpath
        runtimeInitialized.produce(
                new RuntimeInitializedClassBuildItem("org.jboss.resteasy.client.jaxrs.engines.ApacheHttpAsyncClient4Engine"));

        // Initialize Keycloak SecretGenerator at runtime because it contains a SecureRandom instance
        // SecureRandom instances must not be cached at build time as they would have stale seed values
        runtimeInitialized.produce(new RuntimeInitializedClassBuildItem("org.keycloak.common.util.SecretGenerator"));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    void avoidRuntimeInitIssueInClientBuilderWrapper(KeycloakRecorder recorder) {
        // Set the client provider to null during static init to avoid runtime issues
        // with the default ClientBuilderWrapper that is not compatible with native mode
        recorder.avoidRuntimeInitIssueInClientBuilderWrapper();
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void setNativeModeClientProvider(KeycloakRecorder recorder) {
        // Set a custom ResteasyClientProvider that uses QuarkusJacksonSerializer
        // which works properly in native mode
        recorder.setNativeModeClientProvider();
    }
}
