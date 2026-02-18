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
package org.apache.camel.quarkus.component.elasticsearch.it;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(ElasticsearchTestResource.class)
class ElasticsearchTest {
    private static final Logger LOG = Logger.getLogger(ElasticsearchTest.class);

    @AfterEach
    public void afterEach() {
        // Clean up all indexed data
        RestAssured.given()
                .queryParam("component", "elasticsearch")
                .queryParam("indexName", "_all")
                .delete("/elasticsearch/delete/index")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchBasicOperations(String component) {
        String indexName = UUID.randomUUID().toString();
        String indexKey = "test-key";
        String indexValue = "Hello Camel Quarkus ElasticSearch";

        // Verify the ElasticSearch server is available
        RestAssured.given()
                .queryParam("component", component)
                .get("/elasticsearch/ping")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Index data
        String indexId = RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .contentType(ContentType.TEXT)
                .body(indexValue)
                .post("/elasticsearch/index")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        // Verify index exists
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Retrieve indexed data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexId", indexId)
                .queryParam("indexKey", indexKey)
                .get("/elasticsearch/get")
                .then()
                .statusCode(200)
                .body(is(indexValue));

        // Update indexed data
        String updatedIndexValue = indexValue + " Updated";
        RestAssured.given()
                .contentType(ContentType.TEXT)
                .queryParam("component", component)
                .queryParam("indexId", indexId)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .body(updatedIndexValue)
                .patch("/elasticsearch/update")
                .then()
                .statusCode(200);

        // Verify updated data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexId", indexId)
                .queryParam("indexKey", indexKey)
                .get("/elasticsearch/get")
                .then()
                .statusCode(200)
                .body(is(updatedIndexValue));

        // Delete indexed data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexId", indexId)
                .delete("/elasticsearch/delete")
                .then()
                .statusCode(204);

        // Verify data deleted
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexId", indexId)
                .queryParam("indexKey", indexKey)
                .get("/elasticsearch/get")
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchBulk(String component) throws Exception {
        // After the ping check
        /*RestAssured.given()
                .queryParam("component", component)
                .get("/elasticsearch/ping")
                .then()
                .statusCode(200);*/

        // Check cluster health before running bulk operation
        String healthJson = queryClusterHealth();
        System.out
                .println("***************** ------------------- *********************** Cluster health before bulk operation: "
                        + healthJson);
        LOG.warn("***************** ------------------- *********************** Cluster health before bulk operation: "
                + healthJson);

        String indexName = UUID.randomUUID().toString();

        String indexId = RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .post("/elasticsearch/bulk")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexId", indexId)
                .queryParam("indexKey", "camel")
                .get("/elasticsearch/get")
                .then()
                .statusCode(200)
                .body(is("quarkus"));
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchDeleteIndex(String component) {
        String indexName = UUID.randomUUID().toString();
        String indexKey = "test-key";
        String indexValue = "Hello Camel Quarkus ElasticSearch";

        // Index data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .contentType(ContentType.TEXT)
                .body(indexValue)
                .post("/elasticsearch/index")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        // Verify index exists
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Delete indexed data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .delete("/elasticsearch/delete/index")
                .then()
                .statusCode(200)
                .body(is("true"));
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchSearch(String component) {
        String indexName = UUID.randomUUID().toString();
        String indexKey = "camel-quarkus";
        String indexValue = "Sub Atomic, Super Fast Camel Quarkus";

        // Index data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .contentType(ContentType.TEXT)
                .body(indexValue)
                .post("/elasticsearch/index")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        // Verify index exists
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Search data
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS).until(() -> {
            String searchResult = RestAssured.given()
                    .queryParam("component", component)
                    .queryParam("indexKey", indexKey)
                    .body("Super Fast")
                    .get("/elasticsearch/search")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .asString();
            return searchResult.equals(indexValue);
        });
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchSearchJSON(String component) {
        String indexName = UUID.randomUUID().toString();
        String indexKey = "camel-quarkus";
        String indexValue = "Sub Atomic, Super Fast Camel Quarkus";

        // Index data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .contentType(ContentType.TEXT)
                .body(indexValue)
                .post("/elasticsearch/index")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        // Verify index exists
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Search data
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS).until(() -> {
            String searchResult = RestAssured.given()
                    .queryParam("component", component)
                    .queryParam("indexKey", indexKey)
                    .body("Super Fast")
                    .get("/elasticsearch/search/json")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .asString();
            return searchResult.equals(indexValue);
        });
    }

    @ParameterizedTest
    @MethodSource("componentNames")
    public void testElasticsearchMultiSearch(String component) {
        String indexName = UUID.randomUUID().toString();
        String indexKey = "camel-quarkus";
        String indexValue = "Sub Atomic, Super Fast Camel Quarkus";

        // Index data
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .queryParam("indexKey", indexKey)
                .contentType(ContentType.TEXT)
                .body(indexValue)
                .post("/elasticsearch/index")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .asString();

        // Verify index exists
        RestAssured.given()
                .queryParam("component", component)
                .queryParam("indexName", indexName)
                .get("/elasticsearch/exists")
                .then()
                .statusCode(200)
                .body(is("true"));

        // Search data
        Awaitility.await().pollInterval(50, TimeUnit.MILLISECONDS).atMost(100, TimeUnit.SECONDS).until(() -> {
            String hits = RestAssured.given()
                    .queryParam("component", component)
                    .queryParam("indexName", indexName)
                    .queryParam("indexKey", indexKey)
                    .body("Sub Atomic,Super Fast,Nonsense")
                    .get("/elasticsearch/search/multi")
                    .then()
                    .statusCode(200)
                    .extract()
                    .body()
                    .asString();
            return hits.equals("2");
        });
    }

    /**
     * Queries the Elasticsearch cluster health status via HTTP.
     * Returns the current status immediately without waiting.
     *
     * @return           The cluster health response as a String (includes error responses)
     * @throws Exception if the request fails
     */
    private String queryClusterHealth() throws Exception {
        String hostAddresses = ConfigProvider.getConfig().getValue("camel.component.elasticsearch.host-addresses",
                String.class);
        String username = ConfigProvider.getConfig().getValue("camel.component.elasticsearch.user", String.class);
        String password = ConfigProvider.getConfig().getValue("camel.component.elasticsearch.password", String.class);

        URL url = new URL("http://" + hostAddresses + "/_cluster/health");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set up Basic Authentication
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        int responseCode = connection.getResponseCode();

        // Read response body (works for both success and error responses)
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        responseCode == HttpURLConnection.HTTP_OK
                                ? connection.getInputStream()
                                : connection.getErrorStream()))) {
            StringBuilder response = new StringBuilder();
            response.append("HTTP ").append(responseCode).append(": ");
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return "***************** ------------------- *********************** " + response.toString();
        }
    }

    /**
     * This method returns array of component names used in test routes.
     * It can be handy e.g. for testing quarkus managed elasticsearch client.
     *
     * @return Component name used in route.
     */
    @SuppressWarnings("unused")
    private static String[] componentNames() {
        return new String[] { "elasticsearch" };
    }
}
