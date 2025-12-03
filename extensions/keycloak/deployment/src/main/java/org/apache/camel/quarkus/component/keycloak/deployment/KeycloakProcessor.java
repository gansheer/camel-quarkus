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
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

class KeycloakProcessor {

    private static final String FEATURE = "camel-keycloak";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        // Register RESTEasy client classes for reflection
        // The Keycloak admin client uses RESTEasy client which instantiates classes via reflection
        // (Apache HTTP Client classes are handled by camel-quarkus-support-httpclient dependency)
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl",
                "org.jboss.resteasy.client.jaxrs.internal.ClientConfiguration",
                "org.jboss.resteasy.client.jaxrs.ResteasyClient")
                .constructors()
                .methods()
                .fields()
                .build());
    }

    @BuildStep
    RuntimeInitializedClassBuildItem runtimeInitializedClasses() {
        // Initialize Apache HTTP Async Client engine at runtime instead of build-time
        // This is an optional RESTEasy engine that requires httpcore-nio which is not on the classpath
        return new RuntimeInitializedClassBuildItem("org.jboss.resteasy.client.jaxrs.engines.ApacheHttpAsyncClient4Engine");
    }
}
