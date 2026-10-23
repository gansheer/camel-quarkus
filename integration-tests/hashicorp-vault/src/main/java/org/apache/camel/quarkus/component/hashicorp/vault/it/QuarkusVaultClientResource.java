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

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Mirrors the operations {@code camel-hashicorp-vault} supports, but served by the quarkiverse Vault client instead of
 * spring-vault's VaultTemplate, so the two can be compared side by side in the same application.
 */
@Path("/quarkus-vault")
@ApplicationScoped
public class QuarkusVaultClientResource {
    @Inject
    VaultClient vaultClient;

    @Path("/secret")
    @POST
    public Response createSecret(
            @QueryParam("secretPath") String secretPath,
            @QueryParam("key") String key,
            @QueryParam("value") String value) throws Exception {

        vaultClient.secrets().kv2().updateSecret(secretPath, null, Map.of(key, value))
                .toCompletableFuture().join();
        return Response.created(new URI("https://camel.apache.org/")).build();
    }

    @Path("/secret/pojo")
    @POST
    public Response createSecretFromPojo(
            @QueryParam("secretPath") String secretPath,
            @QueryParam("secretA") String secretA,
            @QueryParam("secretB") String secretB,
            @QueryParam("secretC") String secretC) throws Exception {

        vaultClient.secrets().kv2()
                .updateSecret(secretPath, null, Map.of("secretA", secretA, "secretB", secretB, "secretC", secretC))
                .toCompletableFuture().join();
        return Response.created(new URI("https://camel.apache.org/")).build();
    }

    @Path("/secret")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSecret(
            @QueryParam("secretPath") String secretPath,
            @QueryParam("version") Integer version) {
        try {
            VaultSecretsKV2ReadSecretData secret = (version == null
                    ? vaultClient.secrets().kv2().readSecret(secretPath)
                    : vaultClient.secrets().kv2().readSecret(secretPath, version))
                    .toCompletableFuture().join();
            return Response.ok(secret.getData()).build();
        } catch (Exception e) {
            return Response.status(404).build();
        }
    }

    @Path("/secret")
    @DELETE
    public void deleteSecret(@QueryParam("secretPath") String secretPath) {
        vaultClient.secrets().kv2().deleteSecret(secretPath).toCompletableFuture().join();
    }

    @Path("/secret/list/all")
    @GET
    public List<String> listSecrets() {
        return vaultClient.secrets().kv2().listSecrets("").toCompletableFuture().join();
    }
}
