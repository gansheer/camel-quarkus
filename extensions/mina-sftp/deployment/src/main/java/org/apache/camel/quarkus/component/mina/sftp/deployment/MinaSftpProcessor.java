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
package org.apache.camel.quarkus.component.mina.sftp.deployment;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilter;
import org.apache.camel.quarkus.core.deployment.spi.CamelServiceFilterBuildItem;

class MinaSftpProcessor {

    private static final String FEATURE = "camel-mina-sftp";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void filterFtpComponents(BuildProducer<CamelServiceFilterBuildItem> serviceFilter) {
        // camel-mina-sftp depends on camel-ftp only for shared base classes (RemoteFileEndpoint, etc.)
        // but doesn't need the actual ftp/ftps/sftp component implementations
        // Filter them out to prevent these components from being registered
        serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("ftp")));
        serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("ftps")));
        serviceFilter.produce(new CamelServiceFilterBuildItem(CamelServiceFilter.forComponent("sftp")));
    }

    @BuildStep
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClass) {
        // FTP/FTPS/SFTP endpoint classes are still on the classpath (from camel-ftp)
        // Initialize them at runtime to avoid build-time class loading failures
        runtimeInitializedClass
                .produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.FtpEndpoint"));
        runtimeInitializedClass
                .produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.FtpsEndpoint"));
        runtimeInitializedClass
                .produce(new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.SftpEndpoint"));

        // MinaSftpOperations accesses Apache SSHD CoreModuleProperties static fields
        // Initialize at runtime to avoid unresolved field errors at build time
        runtimeInitializedClass
                .produce(
                        new RuntimeInitializedClassBuildItem("org.apache.camel.component.file.remote.mina.MinaSftpOperations"));
    }

}
