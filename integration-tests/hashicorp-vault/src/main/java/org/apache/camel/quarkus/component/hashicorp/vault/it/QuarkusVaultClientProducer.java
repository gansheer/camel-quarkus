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
package org.apache.camel.quarkus.component.hashicorp.vault.it;

import java.net.http.HttpClient;

import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.http.jdk.JDKVaultHttpClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds a Vault client from the quarkiverse generator, backed by java.net.http.HttpClient. This is the springless
 * counterpart of {@link HashicorpVaultProducers}, which needs spring-vault-core and spring-web.
 */
@ApplicationScoped
public class QuarkusVaultClientProducer {
    @ConfigProperty(name = "camel.vault.hashicorp.scheme")
    String scheme;

    @ConfigProperty(name = "camel.vault.hashicorp.host")
    String host;

    @ConfigProperty(name = "camel.vault.hashicorp.port")
    int port;

    @ConfigProperty(name = "camel.vault.hashicorp.token")
    String token;

    @Produces
    @Singleton
    VaultClient vaultClient() {
        // HttpClient picks up the default SSLContext installed by HashicorpVaultClientSSL
        return VaultClient.builder()
                .baseUrl("%s://%s:%d".formatted(scheme, host, port))
                .clientToken(token)
                .executor(new JDKVaultHttpClient(HttpClient.newHttpClient()))
                .build();
    }
}
