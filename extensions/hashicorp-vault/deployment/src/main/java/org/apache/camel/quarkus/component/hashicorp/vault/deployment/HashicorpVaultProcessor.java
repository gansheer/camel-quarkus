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
package org.apache.camel.quarkus.component.hashicorp.vault.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.springframework.vault.support.VaultResponseSupport;

class HashicorpVaultProcessor {
    private static final String FEATURE = "camel-hashicorp-vault";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    IndexDependencyBuildItem indexDependencies() {
        return new IndexDependencyBuildItem("org.springframework.vault", "spring-vault-core");
    }

    // POC for https://github.com/apache/camel-quarkus/issues/3345
    @BuildStep
    IndexDependencyBuildItem indexVaultClientDependencies() {
        return new IndexDependencyBuildItem("io.quarkiverse.vault", "quarkus-vault-client");
    }

    /**
     * Jackson needs reflective access to the generated Vault model types, including records such as
     * VaultErrorResponse. Mirrors what quarkus-vault's own VaultProcessor does.
     */
    @BuildStep
    void registerVaultClientModelsForReflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        String[] vaultModelClasses = combinedIndex.getIndex()
                .getAllKnownImplementations(DotName.createSimple("io.quarkus.vault.client.common.VaultModel"))
                .stream()
                .map(ClassInfo::name)
                .map(DotName::toString)
                .toArray(String[]::new);

        if (vaultModelClasses.length > 0) {
            reflectiveClass.produce(
                    ReflectiveClassBuildItem.builder(vaultModelClasses).constructors(true).methods(true).fields(true).build());
        }
    }

    @BuildStep
    void registerForReflection(CombinedIndexBuildItem combinedIndex, BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        String[] vaultResponseClasses = combinedIndex.getIndex()
                .getAllKnownSubclasses(VaultResponseSupport.class)
                .stream()
                .map(ClassInfo::name)
                .map(DotName::toString)
                .toArray(String[]::new);

        reflectiveClass.produce(ReflectiveClassBuildItem.builder(VaultResponseSupport.class).methods(true).build());
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(vaultResponseClasses).methods(true).build());
    }
}
