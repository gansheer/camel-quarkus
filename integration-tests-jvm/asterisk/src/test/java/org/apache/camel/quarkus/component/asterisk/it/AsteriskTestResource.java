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

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class AsteriskTestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = LoggerFactory.getLogger(AsteriskTestResource.class);

    private static final int AMI_PORT = 5038;
    private static final int SIP_PORT = 5060;
    private static final String ASTERISK_IMAGE = "andrius/asterisk:18-current";

    private AsteriskContainer asteriskContainer;

    /**
     * Custom container that maps AMI port 5038 to fixed host port 5038.
     * This is required because the camel-asterisk component does not support custom ports.
     */
    private static class AsteriskContainer extends GenericContainer<AsteriskContainer> {
        public AsteriskContainer(DockerImageName imageName) {
            super(imageName);
            addFixedExposedPort(AMI_PORT, AMI_PORT);
        }
    }

    @Override
    public Map<String, String> start() {
        try {
            LOG.info("Starting Asterisk container");

            asteriskContainer = new AsteriskContainer(DockerImageName.parse(ASTERISK_IMAGE))
                    .withExposedPorts(SIP_PORT)
                    .withPrivilegedMode(true)
                    .withClasspathResourceMapping("manager.conf", "/etc/asterisk/manager.conf", BindMode.READ_ONLY)
                    .waitingFor(Wait.forLogMessage(".*Asterisk Ready.*", 1))
                    .withLogConsumer(new Slf4jLogConsumer(LOG));

            asteriskContainer.start();

            String host = asteriskContainer.getHost();

            LOG.info("Asterisk container started - Host: {}, AMI Port: {}", host, AMI_PORT);

            Map<String, String> conf = new HashMap<>();
            conf.put("asterisk.host", host);
            conf.put("asterisk.ami.username", "admin");
            conf.put("asterisk.ami.password", "admin");

            return conf;
        } catch (Exception e) {
            LOG.error("Failed to start Asterisk container", e);
            throw new RuntimeException("Failed to start Asterisk container", e);
        }
    }

    @Override
    public void stop() {
        try {
            if (asteriskContainer != null) {
                LOG.info("Stopping Asterisk container");
                asteriskContainer.stop();
            }
        } catch (Exception e) {
            LOG.error("Error stopping Asterisk container", e);
        }
    }
}
