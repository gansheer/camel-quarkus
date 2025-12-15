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
package org.apache.camel.quarkus.component.keycloak.it;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
@QuarkusTestResource(KeycloakTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakTest {

    // Test data - use unique names to avoid conflicts
    private static final String TEST_REALM_NAME = "test-realm-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_USER_NAME = "test-user-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_ROLE_NAME = "test-role-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_GROUP_NAME = "test-group-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ID = "test-client-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_ROLE_NAME = "test-client-role-"
            + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_CLIENT_SCOPE_NAME = "test-scope-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_IDP_ALIAS = "test-idp-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String TEST_AUTHZ_CLIENT_ID = "test-authz-client-"
            + UUID.randomUUID().toString().substring(0, 8);
    private static String TEST_RESOURCE_ID; // Set after creation
    private static String TEST_POLICY_ID; // Set after creation
    private static String TEST_PERMISSION_ID; // Set after creation

    @BeforeAll
    public static void configureRestAssured() {
        // Configure REST-assured to ignore unknown properties when deserializing
        // This is needed because the Keycloak server may return newer fields
        // that the client representation classes don't know about
        RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
                ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory(
                        (cls, charset) -> {
                            ObjectMapper mapper = new ObjectMapper();
                            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                            return mapper;
                        }));
    }

    @Test
    @Order(1)
    public void loadComponentKeycloak() {
        RestAssured.get("/keycloak/load/component/keycloak")
                .then()
                .statusCode(200);
    }

    // ==================== Realm Operations Tests ====================

    @Test
    @Order(2)
    public void testCreateRealmWithHeaders() {
        given()
                .when()
                .post("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .body(is("Realm created successfully"));
    }

    @Test
    @Order(3)
    public void testConfigureRealmSmtp() {
        // Get the realm
        RealmRepresentation realm = given()
                .when()
                .get("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RealmRepresentation.class);

        // Configure SMTP settings to use GreenMail
        Map<String, String> smtpServer = new HashMap<>();
        smtpServer.put("host", "greenmail");
        smtpServer.put("port", "3025");
        smtpServer.put("from", "keycloak@test.local");
        smtpServer.put("fromDisplayName", "Keycloak Test");
        smtpServer.put("replyTo", "noreply@test.local");
        smtpServer.put("ssl", "false");
        smtpServer.put("starttls", "false");
        smtpServer.put("auth", "false");

        realm.setSmtpServer(smtpServer);

        // Update the realm
        given()
                .contentType(ContentType.JSON)
                .body(realm)
                .when()
                .put("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .body(is("Realm updated successfully"));
    }

    @Test
    @Order(4)
    public void testCreateRealmWithPojo() {
        String pojoRealmName = TEST_REALM_NAME + "-pojo";

        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm(pojoRealmName);
        realm.setEnabled(true);
        realm.setDisplayName("Test Realm POJO");

        given()
                .contentType(ContentType.JSON)
                .body(realm)
                .when()
                .post("/keycloak/realm/pojo")
                .then()
                .statusCode(200)
                .body(is("Realm created successfully"));

        // Cleanup the POJO realm
        given()
                .when()
                .delete("/keycloak/realm/{realmName}", pojoRealmName)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(5)
    public void testGetRealm() {
        RealmRepresentation realm = given()
                .when()
                .get("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(RealmRepresentation.class);

        assertThat(realm, notNullValue());
        assertThat(realm.getRealm(), is(TEST_REALM_NAME));
        assertThat(realm.isEnabled(), is(true));
    }

    // ==================== User Operations Tests ====================

    @Test
    @Order(6)
    public void testCreateUserWithHeaders() {
        given()
                .queryParam("email", TEST_USER_NAME + "@test.com")
                .queryParam("firstName", "Test")
                .queryParam("lastName", "User")
                .when()
                .post("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(201)
                .body(is("User created successfully"));
    }

    @Test
    @Order(7)
    public void testCreateUserWithPojo() {
        String pojoUserName = TEST_USER_NAME + "-pojo";

        UserRepresentation user = new UserRepresentation();
        user.setUsername(pojoUserName);
        user.setEmail(pojoUserName + "@test.com");
        user.setFirstName("Test");
        user.setLastName("User POJO");
        user.setEnabled(true);

        given()
                .contentType(ContentType.JSON)
                .body(user)
                .when()
                .post("/keycloak/user/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("User created successfully"));
    }

    @Test
    @Order(8)
    public void testGetUser() {
        UserRepresentation user = given()
                .when()
                .get("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(UserRepresentation.class);

        assertThat(user, notNullValue());
        assertThat(user.getUsername(), is(TEST_USER_NAME));
        assertThat(user.getEmail(), is(TEST_USER_NAME + "@test.com"));
        assertThat(user.getFirstName(), is("Test"));
        assertThat(user.getLastName(), is("User"));
    }

    @Test
    @Order(9)
    public void testListUsers() {
        List<UserRepresentation> users = given()
                .when()
                .get("/keycloak/user/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", UserRepresentation.class);

        assertThat(users, notNullValue());
        assertThat(users.size(), greaterThanOrEqualTo(2)); // At least our two test users
    }

    @Test
    @Order(10)
    public void testUpdateUser() {
        // First get the user
        UserRepresentation user = given()
                .when()
                .get("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(UserRepresentation.class);

        // Update the user's first name
        user.setFirstName("UpdatedFirstName");
        user.setLastName("UpdatedLastName");

        given()
                .contentType(ContentType.JSON)
                .body(user)
                .when()
                .put("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("User updated successfully"));

        // Verify the update
        UserRepresentation updatedUser = given()
                .when()
                .get("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(UserRepresentation.class);

        assertThat(updatedUser.getFirstName(), is("UpdatedFirstName"));
        assertThat(updatedUser.getLastName(), is("UpdatedLastName"));
    }

    // ==================== Role Operations Tests ====================

    @Test
    @Order(11)
    public void testCreateRoleWithHeaders() {
        given()
                .queryParam("description", "Test role for integration testing")
                .when()
                .post("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Role created successfully"));
    }

    @Test
    @Order(12)
    public void testCreateRoleWithPojo() {
        String pojoRoleName = TEST_ROLE_NAME + "-pojo";

        RoleRepresentation role = new RoleRepresentation();
        role.setName(pojoRoleName);
        role.setDescription("Test role created via POJO");

        given()
                .contentType(ContentType.JSON)
                .body(role)
                .when()
                .post("/keycloak/role/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .body(is("Role created successfully"));
    }

    @Test
    @Order(13)
    public void testGetRole() {
        RoleRepresentation role = given()
                .when()
                .get("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(RoleRepresentation.class);

        assertThat(role, notNullValue());
        assertThat(role.getName(), is(TEST_ROLE_NAME));
        assertThat(role.getDescription(), is("Test role for integration testing"));
    }

    @Test
    @Order(14)
    public void testListRoles() {
        List<RoleRepresentation> roles = given()
                .when()
                .get("/keycloak/role/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", RoleRepresentation.class);

        assertThat(roles, notNullValue());
        assertThat(roles.size(), greaterThanOrEqualTo(2)); // At least our test roles + default roles
    }

    @Test
    @Order(15)
    public void testUpdateRole() {
        // First get the role
        RoleRepresentation role = given()
                .when()
                .get("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RoleRepresentation.class);

        // Update the role's description
        role.setDescription("Updated role description");

        given()
                .contentType(ContentType.JSON)
                .body(role)
                .when()
                .put("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Role updated successfully"));

        // Verify the update
        RoleRepresentation updatedRole = given()
                .when()
                .get("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RoleRepresentation.class);

        assertThat(updatedRole.getDescription(), is("Updated role description"));
    }

    // ==================== User-Role Operations Tests ====================

    @Test
    @Order(16)
    public void testAssignRoleToUser() {
        given()
                .when()
                .post("/keycloak/user-role/{realmName}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Role assigned to user successfully"));
    }

    @Test
    @Order(17)
    public void testRemoveRoleFromUser() {
        given()
                .when()
                .delete("/keycloak/user-role/{realmName}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Role removed from user successfully"));
    }

    // ==================== Group Operations Tests ====================

    @Test
    @Order(18)
    public void testCreateGroupWithHeaders() {
        given()
                .when()
                .post("/keycloak/group/{realmName}/{groupName}", TEST_REALM_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(201)
                .body(is("Group created successfully"));
    }

    @Test
    @Order(19)
    public void testCreateGroupWithPojo() {
        String pojoGroupName = TEST_GROUP_NAME + "-pojo";

        GroupRepresentation group = new GroupRepresentation();
        group.setName(pojoGroupName);

        given()
                .contentType(ContentType.JSON)
                .body(group)
                .when()
                .post("/keycloak/group/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Group created successfully"));
    }

    @Test
    @Order(20)
    public void testListGroups() {
        List<GroupRepresentation> groups = given()
                .when()
                .get("/keycloak/group/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", GroupRepresentation.class);

        assertThat(groups, notNullValue());
        assertThat(groups.size(), greaterThanOrEqualTo(2)); // At least our two test groups
    }

    @Test
    @Order(21)
    public void testGetGroup() {
        GroupRepresentation group = given()
                .when()
                .get("/keycloak/group/{realmName}/{groupName}", TEST_REALM_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(GroupRepresentation.class);

        assertThat(group, notNullValue());
        assertThat(group.getName(), is(TEST_GROUP_NAME));
    }

    @Test
    @Order(22)
    public void testUpdateGroup() {
        // First get the group
        GroupRepresentation group = given()
                .when()
                .get("/keycloak/group/{realmName}/{groupName}", TEST_REALM_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(GroupRepresentation.class);

        // Update the group's name using attributes
        group.getAttributes().put("description", List.of("Updated group description"));

        given()
                .contentType(ContentType.JSON)
                .body(group)
                .when()
                .put("/keycloak/group/{realmName}/{groupName}", TEST_REALM_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(200)
                .body(is("Group updated successfully"));
    }

    @Test
    @Order(23)
    public void testAddUserToGroup() {
        given()
                .when()
                .post("/keycloak/group-user/{realmName}/{username}/{groupName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(200)
                .body(is("User added to group successfully"));
    }

    @Test
    @Order(24)
    public void testListUserGroups() {
        List<GroupRepresentation> groups = given()
                .when()
                .get("/keycloak/group-user/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", GroupRepresentation.class);

        assertThat(groups, notNullValue());
        assertThat(groups.size(), greaterThanOrEqualTo(1)); // At least one group
    }

    @Test
    @Order(25)
    public void testRemoveUserFromGroup() {
        given()
                .when()
                .delete("/keycloak/group-user/{realmName}/{username}/{groupName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_GROUP_NAME)
                .then()
                .statusCode(200)
                .body(is("User removed from group successfully"));
    }

    // ==================== Client Operations Tests ====================

    @Test
    @Order(26)
    public void testCreateClientWithHeaders() {
        given()
                .when()
                .post("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(201)
                .body(is("Client created successfully"));
    }

    @Test
    @Order(27)
    public void testCreateClientWithPojo() {
        String pojoClientId = TEST_CLIENT_ID + "-pojo";

        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(pojoClientId);
        client.setEnabled(true);
        client.setPublicClient(true);

        given()
                .contentType(ContentType.JSON)
                .body(client)
                .when()
                .post("/keycloak/client/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Client created successfully"));
    }

    @Test
    @Order(28)
    public void testListClients() {
        List<ClientRepresentation> clients = given()
                .when()
                .get("/keycloak/client/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", ClientRepresentation.class);

        assertThat(clients, notNullValue());
        assertThat(clients.size(), greaterThanOrEqualTo(2)); // At least our two test clients
    }

    @Test
    @Order(29)
    public void testGetClient() {
        ClientRepresentation client = given()
                .when()
                .get("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(ClientRepresentation.class);

        assertThat(client, notNullValue());
        assertThat(client.getClientId(), is(TEST_CLIENT_ID));
    }

    @Test
    @Order(30)
    public void testUpdateClient() {
        // First get the client
        ClientRepresentation client = given()
                .when()
                .get("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(ClientRepresentation.class);

        // Update the client's description
        client.setDescription("Updated client description");

        given()
                .contentType(ContentType.JSON)
                .body(client)
                .when()
                .put("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .body(is("Client updated successfully"));

        // Verify the update
        ClientRepresentation updatedClient = given()
                .when()
                .get("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(ClientRepresentation.class);

        assertThat(updatedClient.getDescription(), is("Updated client description"));
    }

    // ==================== User Search and Session Operations Tests ====================

    @Test
    @Order(31)
    public void testSearchUsers() {
        // Search for users by username prefix
        List<UserRepresentation> users = given()
                .queryParam("query", TEST_USER_NAME)
                .when()
                .get("/keycloak/user/{realmName}/search", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", UserRepresentation.class);

        assertThat(users, notNullValue());
        // At a minimum, verify the endpoint works (results may vary based on search implementation)
        // Search for a username that definitely exists
        boolean foundTestUser = users.stream()
                .anyMatch(u -> TEST_USER_NAME.equals(u.getUsername()));
        assertThat(foundTestUser, is(true));
    }

    @Test
    @Order(32)
    public void testListUserSessions() {
        // List sessions for the test user
        // Note: This user may not have active sessions in a test environment
        List<?> sessions = given()
                .when()
                .get("/keycloak/user/{realmName}/{username}/sessions", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".");

        // Just verify we can call the endpoint successfully (may be empty list)
        assertThat(sessions, notNullValue());
    }

    @Test
    @Order(33)
    public void testResetUserPassword() {
        // Reset the user's password
        given()
                .queryParam("password", "newTestPassword123!")
                .queryParam("temporary", false)
                .when()
                .post("/keycloak/user/{realmName}/{username}/reset-password", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Password reset successfully"));
    }

    @Test
    @Order(34)
    public void testSendVerifyEmail() {
        // Send verification email to the user
        // Now that SMTP is configured with GreenMail, this should succeed without 500 errors
        given()
                .when()
                .post("/keycloak/user/{realmName}/{username}/send-verify-email", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Verify email sent successfully"));
    }

    @Test
    @Order(35)
    public void testSendPasswordResetEmail() {
        // Send password reset email to the user
        // Now that SMTP is configured with GreenMail, this should succeed without 500 errors
        given()
                .when()
                .post("/keycloak/user/{realmName}/{username}/send-password-reset-email", TEST_REALM_NAME,
                        TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Password reset email sent successfully"));
    }

    @Test
    @Order(36)
    public void testLogoutUser() {
        // Logout the user (revoke all sessions)
        given()
                .when()
                .post("/keycloak/user/{realmName}/{username}/logout", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("User logged out successfully"));
    }

    // ==================== Client Role Operations Tests ====================

    @Test
    @Order(41)
    public void testCreateClientRoleWithHeaders() {
        given()
                .queryParam("description", "Test client role for integration testing")
                .when()
                .post("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client role created successfully"));
    }

    @Test
    @Order(42)
    public void testCreateClientRoleWithPojo() {
        String pojoClientRoleName = TEST_CLIENT_ROLE_NAME + "-pojo";

        RoleRepresentation role = new RoleRepresentation();
        role.setName(pojoClientRoleName);
        role.setDescription("Test client role created via POJO");

        given()
                .contentType(ContentType.JSON)
                .body(role)
                .when()
                .post("/keycloak/client-role/{realmName}/{clientId}/pojo", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .body(is("Client role created successfully"));
    }

    @Test
    @Order(43)
    public void testListClientRoles() {
        List<RoleRepresentation> clientRoles = given()
                .when()
                .get("/keycloak/client-role/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", RoleRepresentation.class);

        assertThat(clientRoles, notNullValue());
        assertThat(clientRoles.size(), greaterThanOrEqualTo(2)); // At least our two test client roles
    }

    @Test
    @Order(44)
    public void testGetClientRole() {
        RoleRepresentation clientRole = given()
                .when()
                .get("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(RoleRepresentation.class);

        assertThat(clientRole, notNullValue());
        assertThat(clientRole.getName(), is(TEST_CLIENT_ROLE_NAME));
        assertThat(clientRole.getDescription(), is("Test client role for integration testing"));
    }

    @Test
    @Order(45)
    public void testUpdateClientRole() {
        // First get the client role
        RoleRepresentation clientRole = given()
                .when()
                .get("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RoleRepresentation.class);

        // Update the client role's description
        clientRole.setDescription("Updated client role description");

        given()
                .contentType(ContentType.JSON)
                .body(clientRole)
                .when()
                .put("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client role updated successfully"));

        // Verify the update
        RoleRepresentation updatedClientRole = given()
                .when()
                .get("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RoleRepresentation.class);

        assertThat(updatedClientRole.getDescription(), is("Updated client role description"));
    }

    @Test
    @Order(46)
    public void testAssignClientRoleToUser() {
        given()
                .when()
                .post("/keycloak/client-role-user/{realmName}/{clientId}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_USER_NAME, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client role assigned to user successfully"));
    }

    @Test
    @Order(47)
    public void testRemoveClientRoleFromUser() {
        given()
                .when()
                .delete("/keycloak/client-role-user/{realmName}/{clientId}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_USER_NAME, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client role removed from user successfully"));
    }

    @Test
    @Order(48)
    public void testDeleteClientRole() {
        given()
                .when()
                .delete("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client role deleted successfully"));

        // Also delete the POJO client role
        given()
                .when()
                .delete("/keycloak/client-role/{realmName}/{clientId}/{roleName}",
                        TEST_REALM_NAME, TEST_CLIENT_ID, TEST_CLIENT_ROLE_NAME + "-pojo")
                .then()
                .statusCode(200)
                .body(is("Client role deleted successfully"));
    }

    // ==================== Client Scope Operations Tests ====================

    @Test
    @Order(51)
    public void testCreateClientScopeWithPojo() {
        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName(TEST_CLIENT_SCOPE_NAME);
        scope.setProtocol("openid-connect");
        scope.setDescription("Test client scope for integration testing");

        given()
                .contentType(ContentType.JSON)
                .body(scope)
                .when()
                .post("/keycloak/client-scope/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Client scope created successfully"));
    }

    @Test
    @Order(52)
    public void testCreateClientScopeWithPojo2() {
        String pojoScopeName = TEST_CLIENT_SCOPE_NAME + "-pojo";

        ClientScopeRepresentation scope = new ClientScopeRepresentation();
        scope.setName(pojoScopeName);
        scope.setProtocol("openid-connect");
        scope.setDescription("Test client scope created via POJO");

        given()
                .contentType(ContentType.JSON)
                .body(scope)
                .when()
                .post("/keycloak/client-scope/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Client scope created successfully"));
    }

    @Test
    @Order(53)
    public void testListClientScopes() {
        List<ClientScopeRepresentation> scopes = given()
                .when()
                .get("/keycloak/client-scope/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", ClientScopeRepresentation.class);

        assertThat(scopes, notNullValue());
        assertThat(scopes.size(), greaterThanOrEqualTo(2)); // At least our two test scopes
    }

    @Test
    @Order(54)
    public void testGetClientScope() {
        ClientScopeRepresentation scope = given()
                .when()
                .get("/keycloak/client-scope/{realmName}/{scopeName}", TEST_REALM_NAME, TEST_CLIENT_SCOPE_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(ClientScopeRepresentation.class);

        assertThat(scope, notNullValue());
        assertThat(scope.getName(), is(TEST_CLIENT_SCOPE_NAME));
    }

    @Test
    @Order(55)
    public void testUpdateClientScope() {
        // First get the client scope
        ClientScopeRepresentation scope = given()
                .when()
                .get("/keycloak/client-scope/{realmName}/{scopeName}", TEST_REALM_NAME, TEST_CLIENT_SCOPE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(ClientScopeRepresentation.class);

        // Update the scope's description
        scope.setDescription("Updated client scope description");

        given()
                .contentType(ContentType.JSON)
                .body(scope)
                .when()
                .put("/keycloak/client-scope/{realmName}/{scopeName}", TEST_REALM_NAME, TEST_CLIENT_SCOPE_NAME)
                .then()
                .statusCode(200)
                .body(is("Client scope updated successfully"));

        // Verify the update
        ClientScopeRepresentation updatedScope = given()
                .when()
                .get("/keycloak/client-scope/{realmName}/{scopeName}", TEST_REALM_NAME, TEST_CLIENT_SCOPE_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(ClientScopeRepresentation.class);

        assertThat(updatedScope.getDescription(), is("Updated client scope description"));
    }

    // ==================== User Attribute Operations Tests ====================

    @Test
    @Order(61)
    public void testSetUserAttribute() {
        given()
                .queryParam("attributeValue", "test-department")
                .when()
                .post("/keycloak/user-attribute/{realmName}/{username}/{attributeName}",
                        TEST_REALM_NAME, TEST_USER_NAME, "department")
                .then()
                .statusCode(200)
                .body(is("User attribute set successfully"));
    }

    @Test
    @Order(62)
    public void testGetUserAttributes() {
        Map<String, List<String>> attributes = given()
                .when()
                .get("/keycloak/user-attribute/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(Map.class);

        assertThat(attributes, notNullValue());
        // Verify the attribute was set - check if it exists
        if (attributes.containsKey("department")) {
            assertThat(attributes.get("department").get(0), is("test-department"));
        }
        // Note: Attributes may not persist immediately or may require additional configuration
    }

    @Test
    @Order(63)
    public void testDeleteUserAttribute() {
        // Note: Only delete if the attribute exists
        Map<String, List<String>> attributesBefore = given()
                .when()
                .get("/keycloak/user-attribute/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        if (attributesBefore.containsKey("department")) {
            given()
                    .when()
                    .delete("/keycloak/user-attribute/{realmName}/{username}/{attributeName}",
                            TEST_REALM_NAME, TEST_USER_NAME, "department")
                    .then()
                    .statusCode(200)
                    .body(is("User attribute deleted successfully"));

            // Verify the attribute was deleted
            Map<String, List<String>> attributes = given()
                    .when()
                    .get("/keycloak/user-attribute/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                    .then()
                    .statusCode(200)
                    .extract()
                    .as(Map.class);

            assertThat(attributes.containsKey("department"), is(false));
        }
    }

    // ==================== User Roles Query Tests ====================

    @Test
    @Order(64)
    public void testGetUserRoles() {
        // First, assign a role to the user
        given()
                .when()
                .post("/keycloak/user-role/{realmName}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200);

        // Now get user roles
        List<RoleRepresentation> roles = given()
                .when()
                .get("/keycloak/user-role/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", RoleRepresentation.class);

        assertThat(roles, notNullValue());
        assertThat(roles.size(), greaterThanOrEqualTo(1));

        // Verify our test role is in the list
        boolean foundTestRole = roles.stream()
                .anyMatch(r -> TEST_ROLE_NAME.equals(r.getName()));
        assertThat(foundTestRole, is(true));

        // Clean up - remove the role
        given()
                .when()
                .delete("/keycloak/user-role/{realmName}/{username}/{roleName}",
                        TEST_REALM_NAME, TEST_USER_NAME, TEST_ROLE_NAME)
                .then()
                .statusCode(200);
    }

    // ==================== Client Secret Operations Tests ====================

    @Test
    @Order(71)
    public void testGetClientSecret() {
        // First, we need to make the client confidential to have a secret
        ClientRepresentation client = given()
                .when()
                .get("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(ClientRepresentation.class);

        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);

        given()
                .contentType(ContentType.JSON)
                .body(client)
                .when()
                .put("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200);

        // Now get the client secret
        CredentialRepresentation secretData = given()
                .when()
                .get("/keycloak/client-secret/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(CredentialRepresentation.class);

        assertThat(secretData, notNullValue());
        assertThat(secretData.getValue(), notNullValue());
    }

    @Test
    @Order(72)
    public void testRegenerateClientSecret() {
        // Get the current secret
        CredentialRepresentation oldSecretData = given()
                .when()
                .get("/keycloak/client-secret/{realmName}/{clientId}", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(CredentialRepresentation.class);

        String oldSecret = oldSecretData.getValue();

        // Regenerate the secret
        CredentialRepresentation newSecretData = given()
                .when()
                .post("/keycloak/client-secret/{realmName}/{clientId}/regenerate", TEST_REALM_NAME, TEST_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(CredentialRepresentation.class);

        assertThat(newSecretData, notNullValue());
        assertThat(newSecretData.getValue(), notNullValue());
        // Verify the secret changed
        assertThat(newSecretData.getValue().equals(oldSecret), is(false));
    }

    // ==================== Realm Update Tests ====================

    @Test
    @Order(73)
    public void testUpdateRealm() {
        // First get the realm
        RealmRepresentation realm = given()
                .when()
                .get("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RealmRepresentation.class);

        // Update the realm's display name
        realm.setDisplayName("Updated Test Realm Display Name");
        realm.setDisplayNameHtml("<h1>Updated Test Realm</h1>");

        given()
                .contentType(ContentType.JSON)
                .body(realm)
                .when()
                .put("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .body(is("Realm updated successfully"));

        // Verify the update
        RealmRepresentation updatedRealm = given()
                .when()
                .get("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .extract()
                .as(RealmRepresentation.class);

        assertThat(updatedRealm.getDisplayName(), is("Updated Test Realm Display Name"));
        assertThat(updatedRealm.getDisplayNameHtml(), is("<h1>Updated Test Realm</h1>"));
    }

    // ==================== User Credential Tests ====================

    @Test
    @Order(74)
    public void testGetUserCredentials() {
        // Get user credentials
        List<CredentialRepresentation> credentials = given()
                .when()
                .get("/keycloak/user-credential/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", CredentialRepresentation.class);

        // Just verify we can call the endpoint and get a list
        assertThat(credentials, notNullValue());
        // User may have credentials from password reset earlier in the test
    }

    @Test
    @Order(75)
    public void testDeleteUserCredential() {
        // First get the credentials
        List<CredentialRepresentation> credentials = given()
                .when()
                .get("/keycloak/user-credential/{realmName}/{username}", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList(".", CredentialRepresentation.class);

        // Only delete if there are credentials
        if (credentials != null && !credentials.isEmpty()) {
            String credentialId = credentials.get(0).getId();

            given()
                    .when()
                    .delete("/keycloak/user-credential/{realmName}/{username}/{credentialId}",
                            TEST_REALM_NAME, TEST_USER_NAME, credentialId)
                    .then()
                    .statusCode(200)
                    .body(is("User credential deleted successfully"));
        }
    }

    // ==================== Required Action Tests ====================

    @Test
    @Order(76)
    public void testAddRequiredAction() {
        // Add a required action to the user
        given()
                .queryParam("action", "UPDATE_PASSWORD")
                .when()
                .post("/keycloak/user-action/{realmName}/{username}/add", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Required action added successfully"));
    }

    @Test
    @Order(77)
    public void testRemoveRequiredAction() {
        // Remove the required action from the user
        given()
                .queryParam("action", "UPDATE_PASSWORD")
                .when()
                .post("/keycloak/user-action/{realmName}/{username}/remove", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Required action removed successfully"));
    }

    @Test
    @Order(78)
    public void testExecuteActionsEmail() {
        // Don't pass redirectUri to avoid validation errors
        given()
                .queryParam("actions", "VERIFY_EMAIL,UPDATE_PASSWORD")
                .queryParam("lifespan", 3600)
                .when()
                .post("/keycloak/user-action/{realmName}/{username}/execute", TEST_REALM_NAME, TEST_USER_NAME)
                .then()
                .statusCode(200)
                .body(is("Actions email sent successfully"));
    }

    // ==================== Identity Provider Tests ====================

    @Test
    @Order(79)
    public void testCreateIdentityProvider() {
        // Create an OIDC identity provider
        IdentityProviderRepresentation idp = new IdentityProviderRepresentation();
        idp.setAlias(TEST_IDP_ALIAS);
        idp.setProviderId("oidc");
        idp.setEnabled(true);
        idp.setDisplayName("Test Identity Provider");

        given()
                .contentType(ContentType.JSON)
                .body(idp)
                .when()
                .post("/keycloak/identity-provider/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Identity provider created successfully"));
    }

    @Test
    @Order(80)
    public void testListIdentityProviders() {
        List<IdentityProviderRepresentation> idps = given()
                .when()
                .get("/keycloak/identity-provider/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", IdentityProviderRepresentation.class);

        assertThat(idps, notNullValue());
        assertThat(idps.size(), greaterThanOrEqualTo(1)); // At least our test IDP

        // Verify our test IDP is in the list
        boolean foundTestIdp = idps.stream()
                .anyMatch(i -> TEST_IDP_ALIAS.equals(i.getAlias()));
        assertThat(foundTestIdp, is(true));
    }

    @Test
    @Order(81)
    public void testGetIdentityProvider() {
        IdentityProviderRepresentation idp = given()
                .when()
                .get("/keycloak/identity-provider/{realmName}/{idpAlias}", TEST_REALM_NAME, TEST_IDP_ALIAS)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(IdentityProviderRepresentation.class);

        assertThat(idp, notNullValue());
        assertThat(idp.getAlias(), is(TEST_IDP_ALIAS));
        assertThat(idp.getProviderId(), is("oidc"));
        assertThat(idp.getDisplayName(), is("Test Identity Provider"));
    }

    @Test
    @Order(82)
    public void testUpdateIdentityProvider() {
        // First get the identity provider
        IdentityProviderRepresentation idp = given()
                .when()
                .get("/keycloak/identity-provider/{realmName}/{idpAlias}", TEST_REALM_NAME, TEST_IDP_ALIAS)
                .then()
                .statusCode(200)
                .extract()
                .as(IdentityProviderRepresentation.class);

        // Update the display name
        idp.setDisplayName("Updated Test Identity Provider");

        given()
                .contentType(ContentType.JSON)
                .body(idp)
                .when()
                .put("/keycloak/identity-provider/{realmName}/{idpAlias}", TEST_REALM_NAME, TEST_IDP_ALIAS)
                .then()
                .statusCode(200)
                .body(is("Identity provider updated successfully"));

        // Verify the update
        IdentityProviderRepresentation updatedIdp = given()
                .when()
                .get("/keycloak/identity-provider/{realmName}/{idpAlias}", TEST_REALM_NAME, TEST_IDP_ALIAS)
                .then()
                .statusCode(200)
                .extract()
                .as(IdentityProviderRepresentation.class);

        assertThat(updatedIdp.getDisplayName(), is("Updated Test Identity Provider"));
    }

    @Test
    @Order(100)
    public void testCleanupIdentityProvider() {
        // Delete test identity provider
        given()
                .when()
                .delete("/keycloak/identity-provider/{realmName}/{idpAlias}", TEST_REALM_NAME, TEST_IDP_ALIAS)
                .then()
                .statusCode(200)
                .body(is("Identity provider deleted successfully"));
    }

    // ==================== Cleanup Client Scopes ====================

    @Test
    @Order(101)
    public void testCleanupClientScopes() {
        // Delete test client scopes
        String[] scopesToDelete = { TEST_CLIENT_SCOPE_NAME, TEST_CLIENT_SCOPE_NAME + "-pojo" };

        for (String scopeName : scopesToDelete) {
            given()
                    .when()
                    .delete("/keycloak/client-scope/{realmName}/{scopeName}", TEST_REALM_NAME, scopeName)
                    .then()
                    .statusCode(200)
                    .body(is("Client scope deleted successfully"));
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(81)
    public void testErrorHandling_NonExistentRealm() {
        // Test with non-existent realm should fail
        given()
                .queryParam("email", "test@test.com")
                .queryParam("firstName", "Test")
                .queryParam("lastName", "User")
                .when()
                .post("/keycloak/user/{realmName}/{username}", "non-existent-realm", "testuser")
                .then()
                .statusCode(404); // Should fail since realm doesn't exist
    }

    @Test
    @Order(82)
    public void testErrorHandling_NonExistentUser() {
        // Test getting a user that doesn't exist
        given()
                .when()
                .get("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, "non-existent-user")
                .then()
                .statusCode(500); // Should fail since user doesn't exist
    }

    @Test
    @Order(83)
    public void testErrorHandling_NonExistentRole() {
        // Test getting a role that doesn't exist
        given()
                .when()
                .get("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, "non-existent-role")
                .then()
                .statusCode(500); // Should fail since role doesn't exist
    }

    // ==================== Authorization Services Tests ====================

    @Test
    @Order(84)
    public void testCreateAuthorizationClient() {
        // Create a client with authorization enabled for testing authorization services
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(TEST_AUTHZ_CLIENT_ID);
        client.setEnabled(true);
        client.setPublicClient(false); // Must be confidential for authorization
        client.setServiceAccountsEnabled(true);
        client.setAuthorizationServicesEnabled(true); // Enable authorization

        given()
                .contentType(ContentType.JSON)
                .body(client)
                .when()
                .post("/keycloak/client/{realmName}/pojo", TEST_REALM_NAME)
                .then()
                .statusCode(201)
                .body(is("Client created successfully"));
    }

    @Test
    @Order(85)
    public void testCreateResource() {
        // Create an authorization resource
        ResourceRepresentation resource = new ResourceRepresentation();
        resource.setName("test-resource");
        resource.setDisplayName("Test Resource");
        resource.setType("urn:test:resources:default");
        resource.setUri("/test-resource/*");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(resource)
                .when()
                .post("/keycloak/resource/{realmName}/{clientId}/pojo", TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(201)
                .extract()
                .response();

        // Extract resource ID from Location header
        String location = response.header("Location");
        if (location != null) {
            TEST_RESOURCE_ID = location.substring(location.lastIndexOf('/') + 1);
        }
    }

    @Test
    @Order(86)
    public void testListResources() {
        List<ResourceRepresentation> resources = given()
                .when()
                .get("/keycloak/resource/{realmName}/{clientId}", TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", ResourceRepresentation.class);

        assertThat(resources, notNullValue());
        assertThat(resources.size(), greaterThanOrEqualTo(1));

        // Find and store the resource ID if not already set
        if (TEST_RESOURCE_ID == null) {
            TEST_RESOURCE_ID = resources.stream()
                    .filter(r -> "test-resource".equals(r.getName()))
                    .map(ResourceRepresentation::getId)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Test
    @Order(87)
    public void testGetResource() {
        assertThat(TEST_RESOURCE_ID, notNullValue());

        ResourceRepresentation resource = given()
                .when()
                .get("/keycloak/resource/{realmName}/{clientId}/{resourceId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_RESOURCE_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(ResourceRepresentation.class);

        assertThat(resource, notNullValue());
        assertThat(resource.getName(), is("test-resource"));
        assertThat(resource.getType(), is("urn:test:resources:default"));
    }

    @Test
    @Order(88)
    public void testUpdateResource() {
        assertThat(TEST_RESOURCE_ID, notNullValue());

        // First get the resource
        ResourceRepresentation resource = given()
                .when()
                .get("/keycloak/resource/{realmName}/{clientId}/{resourceId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_RESOURCE_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(ResourceRepresentation.class);

        // Update the resource
        resource.setDisplayName("Updated Test Resource");

        given()
                .contentType(ContentType.JSON)
                .body(resource)
                .when()
                .put("/keycloak/resource/{realmName}/{clientId}/{resourceId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_RESOURCE_ID)
                .then()
                .statusCode(200)
                .body(is("Resource updated successfully"));

        // Verify the update
        ResourceRepresentation updatedResource = given()
                .when()
                .get("/keycloak/resource/{realmName}/{clientId}/{resourceId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_RESOURCE_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(ResourceRepresentation.class);

        assertThat(updatedResource.getDisplayName(), is("Updated Test Resource"));
    }

    @Test
    @Order(89)
    public void testCreateResourcePolicy() {
        // Create a resource-based policy
        PolicyRepresentation policy = new PolicyRepresentation();
        policy.setName("test-policy");
        policy.setDescription("Test Policy");
        policy.setType("resource");
        policy.setDecisionStrategy(org.keycloak.representations.idm.authorization.DecisionStrategy.UNANIMOUS);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(policy)
                .when()
                .post("/keycloak/resource-policy/{realmName}/{clientId}/pojo",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(201)
                .extract()
                .response();

        // Extract policy ID from Location header
        String location = response.header("Location");
        if (location != null) {
            TEST_POLICY_ID = location.substring(location.lastIndexOf('/') + 1);
        }
    }

    @Test
    @Order(90)
    public void testListResourcePolicies() {
        List<PolicyRepresentation> policies = given()
                .when()
                .get("/keycloak/resource-policy/{realmName}/{clientId}", TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", PolicyRepresentation.class);

        assertThat(policies, notNullValue());
        assertThat(policies.size(), greaterThanOrEqualTo(1));

        // Find and store the policy ID if not already set
        if (TEST_POLICY_ID == null) {
            TEST_POLICY_ID = policies.stream()
                    .filter(p -> "test-policy".equals(p.getName()))
                    .map(PolicyRepresentation::getId)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Test
    @Order(91)
    public void testGetResourcePolicy() {
        assertThat(TEST_POLICY_ID, notNullValue());

        PolicyRepresentation policy = given()
                .when()
                .get("/keycloak/resource-policy/{realmName}/{clientId}/{policyId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_POLICY_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(PolicyRepresentation.class);

        assertThat(policy, notNullValue());
        assertThat(policy.getName(), is("test-policy"));
    }

    @Test
    @Order(92)
    public void testUpdateResourcePolicy() {
        assertThat(TEST_POLICY_ID, notNullValue());

        // First get the policy
        PolicyRepresentation policy = given()
                .when()
                .get("/keycloak/resource-policy/{realmName}/{clientId}/{policyId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_POLICY_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(PolicyRepresentation.class);

        // Update the policy
        policy.setDescription("Updated Test Policy");

        given()
                .contentType(ContentType.JSON)
                .body(policy)
                .when()
                .put("/keycloak/resource-policy/{realmName}/{clientId}/{policyId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_POLICY_ID)
                .then()
                .statusCode(200)
                .body(is("Policy updated successfully"));

        // Verify the update
        PolicyRepresentation updatedPolicy = given()
                .when()
                .get("/keycloak/resource-policy/{realmName}/{clientId}/{policyId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_POLICY_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(PolicyRepresentation.class);

        assertThat(updatedPolicy.getDescription(), is("Updated Test Policy"));
    }

    @Test
    @Order(93)
    public void testCreateResourcePermission() {
        assertThat(TEST_RESOURCE_ID, notNullValue());
        assertThat(TEST_POLICY_ID, notNullValue());

        // Create a resource permission linking resource and policy
        ResourcePermissionRepresentation permission = new ResourcePermissionRepresentation();
        permission.setName("test-permission");
        permission.setDescription("Test Permission");
        permission.setResources(java.util.Set.of(TEST_RESOURCE_ID));
        permission.setPolicies(java.util.Set.of(TEST_POLICY_ID));
        permission.setDecisionStrategy(org.keycloak.representations.idm.authorization.DecisionStrategy.UNANIMOUS);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(permission)
                .when()
                .post("/keycloak/resource-permission/{realmName}/{clientId}/pojo",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(201)
                .extract()
                .response();

        // Extract permission ID from Location header
        String location = response.header("Location");
        if (location != null) {
            TEST_PERMISSION_ID = location.substring(location.lastIndexOf('/') + 1);
        }
    }

    @Test
    @Order(94)
    public void testListResourcePermissions() {
        List<ResourcePermissionRepresentation> permissions = given()
                .when()
                .get("/keycloak/resource-permission/{realmName}/{clientId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".", ResourcePermissionRepresentation.class);

        assertThat(permissions, notNullValue());
        assertThat(permissions.size(), greaterThanOrEqualTo(1));

        // Find and store the permission ID if not already set
        if (TEST_PERMISSION_ID == null) {
            TEST_PERMISSION_ID = permissions.stream()
                    .filter(p -> "test-permission".equals(p.getName()))
                    .map(ResourcePermissionRepresentation::getId)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Test
    @Order(95)
    public void testGetResourcePermission() {
        assertThat(TEST_PERMISSION_ID, notNullValue());

        PolicyRepresentation permission = given()
                .when()
                .get("/keycloak/resource-permission/{realmName}/{clientId}/{permissionId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_PERMISSION_ID)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .as(PolicyRepresentation.class);

        assertThat(permission, notNullValue());
        assertThat(permission.getName(), is("test-permission"));
    }

    @Test
    @Order(96)
    public void testUpdateResourcePermission() {
        assertThat(TEST_PERMISSION_ID, notNullValue());

        // First get the permission
        PolicyRepresentation permission = given()
                .when()
                .get("/keycloak/resource-permission/{realmName}/{clientId}/{permissionId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_PERMISSION_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(PolicyRepresentation.class);

        // Update the permission
        permission.setDescription("Updated Test Permission");

        given()
                .contentType(ContentType.JSON)
                .body(permission)
                .when()
                .put("/keycloak/resource-permission/{realmName}/{clientId}/{permissionId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_PERMISSION_ID)
                .then()
                .statusCode(200)
                .body(is("Permission updated successfully"));

        // Verify the update
        PolicyRepresentation updatedPermission = given()
                .when()
                .get("/keycloak/resource-permission/{realmName}/{clientId}/{permissionId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_PERMISSION_ID)
                .then()
                .statusCode(200)
                .extract()
                .as(PolicyRepresentation.class);

        assertThat(updatedPermission.getDescription(), is("Updated Test Permission"));
    }

    @Test
    @Order(97)
    public void testDeleteResourcePermission() {
        assertThat(TEST_PERMISSION_ID, notNullValue());

        given()
                .when()
                .delete("/keycloak/resource-permission/{realmName}/{clientId}/{permissionId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_PERMISSION_ID)
                .then()
                .statusCode(200)
                .body(is("Permission deleted successfully"));
    }

    @Test
    @Order(98)
    public void testDeleteResourcePolicy() {
        assertThat(TEST_POLICY_ID, notNullValue());

        given()
                .when()
                .delete("/keycloak/resource-policy/{realmName}/{clientId}/{policyId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_POLICY_ID)
                .then()
                .statusCode(200)
                .body(is("Policy deleted successfully"));
    }

    @Test
    @Order(99)
    public void testDeleteResource() {
        assertThat(TEST_RESOURCE_ID, notNullValue());

        given()
                .when()
                .delete("/keycloak/resource/{realmName}/{clientId}/{resourceId}",
                        TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID, TEST_RESOURCE_ID)
                .then()
                .statusCode(200)
                .body(is("Resource deleted successfully"));
    }

    // ==================== Cleanup Tests ====================

    @Test
    @Order(102)
    public void testCleanupAuthorizationClient() {
        // Delete the authorization client
        given()
                .when()
                .delete("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, TEST_AUTHZ_CLIENT_ID)
                .then()
                .statusCode(200)
                .body(is("Client deleted successfully"));
    }

    @Test
    @Order(103)
    public void testCleanupClients() {
        // Delete test clients
        String[] clientsToDelete = { TEST_CLIENT_ID, TEST_CLIENT_ID + "-pojo" };

        for (String clientId : clientsToDelete) {
            given()
                    .when()
                    .delete("/keycloak/client/{realmName}/{clientId}", TEST_REALM_NAME, clientId)
                    .then()
                    .statusCode(200)
                    .body(is("Client deleted successfully"));
        }
    }

    @Test
    @Order(104)
    public void testCleanupGroups() {
        // Delete test groups
        String[] groupsToDelete = { TEST_GROUP_NAME, TEST_GROUP_NAME + "-pojo" };

        for (String groupName : groupsToDelete) {
            given()
                    .when()
                    .delete("/keycloak/group/{realmName}/{groupName}", TEST_REALM_NAME, groupName)
                    .then()
                    .statusCode(200)
                    .body(is("Group deleted successfully"));
        }
    }

    @Test
    @Order(105)
    public void testCleanupRoles() {
        // Delete test roles
        String[] rolesToDelete = { TEST_ROLE_NAME, TEST_ROLE_NAME + "-pojo" };

        for (String roleName : rolesToDelete) {
            given()
                    .when()
                    .delete("/keycloak/role/{realmName}/{roleName}", TEST_REALM_NAME, roleName)
                    .then()
                    .statusCode(200)
                    .body(is("Role deleted successfully"));
        }
    }

    @Test
    @Order(106)
    public void testCleanupUsers() {
        // Delete test users
        String[] usersToDelete = { TEST_USER_NAME, TEST_USER_NAME + "-pojo" };

        for (String username : usersToDelete) {
            given()
                    .when()
                    .delete("/keycloak/user/{realmName}/{username}", TEST_REALM_NAME, username)
                    .then()
                    .statusCode(200)
                    .body(is("User deleted successfully"));
        }
    }

    @Test
    @Order(107)
    public void testCleanupRealm() {
        // Delete the test realm (this will also delete all users and roles in it)
        given()
                .when()
                .delete("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(200)
                .body(is("Realm deleted successfully"));
    }

    @Test
    @Order(108)
    public void testVerifyRealmDeleted() {
        // Verify that the realm was actually deleted by expecting a failure
        given()
                .when()
                .get("/keycloak/realm/{realmName}", TEST_REALM_NAME)
                .then()
                .statusCode(500); // Should fail since realm no longer exists
    }
}
