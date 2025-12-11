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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.keycloak.KeycloakConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.authorization.PolicyRepresentation;
import org.keycloak.representations.idm.authorization.ResourcePermissionRepresentation;
import org.keycloak.representations.idm.authorization.ResourceRepresentation;

@Path("/keycloak")
@ApplicationScoped
public class KeycloakResource {

    private static final Logger LOG = Logger.getLogger(KeycloakResource.class);

    private static final String COMPONENT_KEYCLOAK = "keycloak";

    @Inject
    CamelContext context;

    @Inject
    ProducerTemplate producerTemplate;

    @ConfigProperty(name = "keycloak.url")
    String keycloakUrl;

    @ConfigProperty(name = "keycloak.username")
    String keycloakUsername;

    @ConfigProperty(name = "keycloak.password")
    String keycloakPassword;

    @ConfigProperty(name = "keycloak.realm")
    String keycloakRealm;

    private String getKeycloakEndpoint() {
        return String.format("keycloak:admin?serverUrl=%s&realm=%s&username=%s&password=%s",
                keycloakUrl, keycloakRealm, keycloakUsername, keycloakPassword);
    }

    @Path("/load/component/keycloak")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response loadComponentKeycloak() throws Exception {
        if (context.getComponent(COMPONENT_KEYCLOAK) != null) {
            return Response.ok().build();
        }
        LOG.warnf("Could not load [%s] from the Camel context", COMPONENT_KEYCLOAK);
        return Response.status(500, COMPONENT_KEYCLOAK + " could not be loaded from the Camel context").build();
    }

    // ==================== Realm Operations ====================

    @Path("/realm/{realmName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createRealmWithHeaders(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createRealm",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/realm/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createRealmWithPojo(RealmRepresentation realm) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realm.getRealm());

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createRealm&pojoRequest=true",
                realm,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/realm/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRealm(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        RealmRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getRealm",
                null,
                headers,
                RealmRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/realm/{realmName}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateRealm(
            @PathParam("realmName") String realmName,
            RealmRepresentation realm) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateRealm&pojoRequest=true",
                realm,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/realm/{realmName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteRealm(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteRealm",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== User Operations ====================

    @Path("/user/{realmName}/{username}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createUserWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @QueryParam("email") String email,
            @QueryParam("firstName") String firstName,
            @QueryParam("lastName") String lastName) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USERNAME, username);
        headers.put(KeycloakConstants.USER_EMAIL, email);
        headers.put(KeycloakConstants.USER_FIRST_NAME, firstName);
        headers.put(KeycloakConstants.USER_LAST_NAME, lastName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createUser",
                null,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("User created successfully")
                    .build();
        }

        return Response.ok("User created successfully").build();
    }

    @Path("/user/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createUserWithPojo(
            @PathParam("realmName") String realmName,
            UserRepresentation user) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createUser&pojoRequest=true",
                user,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("User created successfully")
                    .build();
        }

        return Response.ok("User created successfully").build();
    }

    @Path("/user/{realmName}/{username}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, list users to find the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        UserRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getUser",
                null,
                headers,
                UserRepresentation.class);

        return Response.ok(result).build();
    }

    /**
     * Helper method to get user ID by username
     */
    private String getUserIdByUsername(String realmName, String username) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<UserRepresentation> users = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listUsers",
                null,
                headers,
                List.class);

        return users.stream()
                .filter(u -> username.equals(u.getUsername()))
                .map(UserRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Path("/user/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<UserRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listUsers",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/user/{realmName}/{username}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteUser",
                null,
                headers);

        return Response.ok("User deleted successfully").build();
    }

    @Path("/user/{realmName}/{username}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            UserRepresentation user) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        // Set the ID in the user object
        user.setId(userId);

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateUser&pojoRequest=true",
                user,
                headers);

        return Response.ok("User updated successfully").build();
    }

    @Path("/user/{realmName}/search")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response searchUsers(
            @PathParam("realmName") String realmName,
            @QueryParam("query") String query) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.SEARCH_QUERY, query);

        @SuppressWarnings("unchecked")
        List<UserRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=searchUsers",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/user/{realmName}/{username}/sessions")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUserSessions(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        @SuppressWarnings("unchecked")
        List result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listUserSessions",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/user/{realmName}/{username}/logout")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response logoutUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=logoutUser",
                null,
                headers);

        return Response.ok("User logged out successfully").build();
    }

    @Path("/user/{realmName}/{username}/reset-password")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response resetUserPassword(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @QueryParam("password") String password,
            @QueryParam("temporary") Boolean temporary) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.USER_PASSWORD, password);
        if (temporary != null) {
            headers.put(KeycloakConstants.PASSWORD_TEMPORARY, temporary);
        }

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=resetUserPassword",
                null,
                headers);

        return Response.ok("Password reset successfully").build();
    }

    @Path("/user/{realmName}/{username}/send-verify-email")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendVerifyEmail(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=sendVerifyEmail",
                null,
                headers);

        return Response.ok("Verify email sent successfully").build();
    }

    @Path("/user/{realmName}/{username}/send-password-reset-email")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response sendPasswordResetEmail(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=sendPasswordResetEmail",
                null,
                headers);

        return Response.ok("Password reset email sent successfully").build();
    }

    // ==================== Role Operations ====================

    @Path("/role/{realmName}/{roleName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createRoleWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("roleName") String roleName,
            @QueryParam("description") String description) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);
        if (description != null) {
            headers.put(KeycloakConstants.ROLE_DESCRIPTION, description);
        }

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createRole",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/role/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createRoleWithPojo(
            @PathParam("realmName") String realmName,
            RoleRepresentation role) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createRole&pojoRequest=true",
                role,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/role/{realmName}/{roleName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRole(
            @PathParam("realmName") String realmName,
            @PathParam("roleName") String roleName) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        RoleRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getRole",
                null,
                headers,
                RoleRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/role/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRoles(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<RoleRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listRoles",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/role/{realmName}/{roleName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteRole(
            @PathParam("realmName") String realmName,
            @PathParam("roleName") String roleName) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteRole",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/role/{realmName}/{roleName}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateRole(
            @PathParam("realmName") String realmName,
            @PathParam("roleName") String roleName,
            RoleRepresentation role) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        // Set the role name in the object
        role.setName(roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateRole&pojoRequest=true",
                role,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== User-Role Operations ====================

    @Path("/user-role/{realmName}/{username}/{roleName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response assignRoleToUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("roleName") String roleName) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=assignRoleToUser",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/user-role/{realmName}/{username}/{roleName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response removeRoleFromUser(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("roleName") String roleName) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=removeRoleFromUser",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Group Operations ====================

    @Path("/group/{realmName}/{groupName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createGroupWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("groupName") String groupName) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.GROUP_NAME, groupName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createGroup",
                null,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Group created successfully")
                    .build();
        }

        return Response.ok("Group created successfully").build();
    }

    @Path("/group/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createGroupWithPojo(
            @PathParam("realmName") String realmName,
            GroupRepresentation group) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createGroup&pojoRequest=true",
                group,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Group created successfully")
                    .build();
        }

        return Response.ok("Group created successfully").build();
    }

    @Path("/group/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGroups(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<GroupRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listGroups",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/group/{realmName}/{groupName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGroup(
            @PathParam("realmName") String realmName,
            @PathParam("groupName") String groupName) {

        // First, list groups to find the group ID by name
        String groupId = getGroupIdByName(realmName, groupName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.GROUP_ID, groupId);

        GroupRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getGroup",
                null,
                headers,
                GroupRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/group/{realmName}/{groupName}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateGroup(
            @PathParam("realmName") String realmName,
            @PathParam("groupName") String groupName,
            GroupRepresentation group) {

        // First, get the group ID by name
        String groupId = getGroupIdByName(realmName, groupName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.GROUP_ID, groupId);

        // Set the ID in the group object
        group.setId(groupId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateGroup&pojoRequest=true",
                group,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/group/{realmName}/{groupName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteGroup(
            @PathParam("realmName") String realmName,
            @PathParam("groupName") String groupName) {

        // First, get the group ID by name
        String groupId = getGroupIdByName(realmName, groupName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.GROUP_ID, groupId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteGroup",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    /**
     * Helper method to get group ID by name
     */
    private String getGroupIdByName(String realmName, String groupName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<GroupRepresentation> groups = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listGroups",
                null,
                headers,
                List.class);

        return groups.stream()
                .filter(g -> groupName.equals(g.getName()))
                .map(GroupRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Group not found: " + groupName));
    }

    // ==================== Group-User Operations ====================

    @Path("/group-user/{realmName}/{username}/{groupName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response addUserToGroup(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("groupName") String groupName) {

        // Get user ID and group ID
        String userId = getUserIdByUsername(realmName, username);
        String groupId = getGroupIdByName(realmName, groupName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.GROUP_ID, groupId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=addUserToGroup",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/group-user/{realmName}/{username}/{groupName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response removeUserFromGroup(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("groupName") String groupName) {

        // Get user ID and group ID
        String userId = getUserIdByUsername(realmName, username);
        String groupId = getGroupIdByName(realmName, groupName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.GROUP_ID, groupId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=removeUserFromGroup",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/group-user/{realmName}/{username}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUserGroups(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // Get user ID
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        @SuppressWarnings("unchecked")
        List<GroupRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listUserGroups",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    // ==================== Client Operations ====================

    @Path("/client/{realmName}/{clientId}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_ID, clientId);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClient",
                null,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Client created successfully")
                    .build();
        }

        return Response.ok("Client created successfully").build();
    }

    @Path("/client/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientWithPojo(
            @PathParam("realmName") String realmName,
            ClientRepresentation client) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClient&pojoRequest=true",
                client,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Client created successfully")
                    .build();
        }

        return Response.ok("Client created successfully").build();
    }

    @Path("/client/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listClients(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<ClientRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listClients",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/client/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClient(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, list clients to find the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        ClientRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getClient",
                null,
                headers,
                ClientRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/client/{realmName}/{clientId}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateClient(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            ClientRepresentation client) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        // Set the ID in the client object
        client.setId(clientUuid);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateClient&pojoRequest=true",
                client,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client/{realmName}/{clientId}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteClient(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteClient",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    /**
     * Helper method to get client UUID by clientId
     */
    private String getClientUuidByClientId(String realmName, String clientId) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<ClientRepresentation> clients = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listClients",
                null,
                headers,
                List.class);

        return clients.stream()
                .filter(c -> clientId.equals(c.getClientId()))
                .map(ClientRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Client not found: " + clientId));
    }

    // ==================== Client Role Operations ====================

    @Path("/client-role/{realmName}/{clientId}/{roleName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientRoleWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("roleName") String roleName,
            @QueryParam("description") String description) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);
        if (description != null) {
            headers.put(KeycloakConstants.ROLE_DESCRIPTION, description);
        }

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClientRole",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client-role/{realmName}/{clientId}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientRoleWithPojo(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            RoleRepresentation role) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClientRole&pojoRequest=true",
                role,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client-role/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listClientRoles(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        @SuppressWarnings("unchecked")
        List<RoleRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listClientRoles",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/client-role/{realmName}/{clientId}/{roleName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClientRole(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("roleName") String roleName) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        RoleRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getClientRole",
                null,
                headers,
                RoleRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/client-role/{realmName}/{clientId}/{roleName}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateClientRole(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("roleName") String roleName,
            RoleRepresentation role) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        // Set the role name
        role.setName(roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateClientRole&pojoRequest=true",
                role,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client-role/{realmName}/{clientId}/{roleName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteClientRole(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("roleName") String roleName) {

        // Get client UUID
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteClientRole",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Client Role - User Assignment Operations ====================

    @Path("/client-role-user/{realmName}/{clientId}/{username}/{roleName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response assignClientRoleToUser(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("username") String username,
            @PathParam("roleName") String roleName) {

        // Get user ID and client UUID
        String userId = getUserIdByUsername(realmName, username);
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=assignClientRoleToUser",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client-role-user/{realmName}/{clientId}/{username}/{roleName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response removeClientRoleFromUser(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("username") String username,
            @PathParam("roleName") String roleName) {

        // Get user ID and client UUID
        String userId = getUserIdByUsername(realmName, username);
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.ROLE_NAME, roleName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=removeClientRoleFromUser",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Client Scope Operations ====================

    @Path("/client-scope/{realmName}/{scopeName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientScopeWithHeaders(
            @PathParam("realmName") String realmName,
            @PathParam("scopeName") String scopeName,
            @QueryParam("protocol") String protocol,
            @QueryParam("description") String description) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_SCOPE_NAME, scopeName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClientScope",
                null,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Client scope created successfully")
                    .build();
        }

        return Response.ok("Client scope created successfully").build();
    }

    @Path("/client-scope/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createClientScopeWithPojo(
            @PathParam("realmName") String realmName,
            ClientScopeRepresentation clientScope) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createClientScope&pojoRequest=true",
                clientScope,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Client scope created successfully")
                    .build();
        }

        return Response.ok("Client scope created successfully").build();
    }

    @Path("/client-scope/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listClientScopes(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<ClientScopeRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listClientScopes",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/client-scope/{realmName}/{scopeName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClientScope(
            @PathParam("realmName") String realmName,
            @PathParam("scopeName") String scopeName) {

        // First, list client scopes to find the scope ID by name
        String scopeId = getClientScopeIdByName(realmName, scopeName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_SCOPE_ID, scopeId);

        ClientScopeRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getClientScope",
                null,
                headers,
                ClientScopeRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/client-scope/{realmName}/{scopeName}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateClientScope(
            @PathParam("realmName") String realmName,
            @PathParam("scopeName") String scopeName,
            ClientScopeRepresentation clientScope) {

        // First, get the client scope ID by name
        String scopeId = getClientScopeIdByName(realmName, scopeName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_SCOPE_ID, scopeId);

        // Set the ID in the client scope object
        clientScope.setId(scopeId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateClientScope&pojoRequest=true",
                clientScope,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/client-scope/{realmName}/{scopeName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteClientScope(
            @PathParam("realmName") String realmName,
            @PathParam("scopeName") String scopeName) {

        // First, get the client scope ID by name
        String scopeId = getClientScopeIdByName(realmName, scopeName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_SCOPE_ID, scopeId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteClientScope",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    /**
     * Helper method to get client scope ID by name
     */
    private String getClientScopeIdByName(String realmName, String scopeName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<ClientScopeRepresentation> scopes = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listClientScopes",
                null,
                headers,
                List.class);

        return scopes.stream()
                .filter(s -> scopeName.equals(s.getName()))
                .map(ClientScopeRepresentation::getId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Client scope not found: " + scopeName));
    }

    // ==================== User Attribute Operations ====================

    @Path("/user-attribute/{realmName}/{username}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserAttributes(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getUserAttributes",
                null,
                headers,
                Map.class);

        return Response.ok(result).build();
    }

    @Path("/user-attribute/{realmName}/{username}/{attributeName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response setUserAttribute(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("attributeName") String attributeName,
            @QueryParam("attributeValue") String attributeValue) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.ATTRIBUTE_NAME, attributeName);
        headers.put(KeycloakConstants.ATTRIBUTE_VALUE, attributeValue);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=setUserAttribute",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/user-attribute/{realmName}/{username}/{attributeName}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteUserAttribute(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("attributeName") String attributeName) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.ATTRIBUTE_NAME, attributeName);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteUserAttribute",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== User Credential Operations ====================

    @Path("/user-credential/{realmName}/{username}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserCredentials(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        @SuppressWarnings("unchecked")
        List<CredentialRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getUserCredentials",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/user-credential/{realmName}/{username}/{credentialId}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteUserCredential(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @PathParam("credentialId") String credentialId) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.CREDENTIAL_ID, credentialId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteUserCredential",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== User Required Action Operations ====================

    @Path("/user-action/{realmName}/{username}/add")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response addRequiredAction(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @QueryParam("action") String action) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.REQUIRED_ACTION, action);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=addRequiredAction",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/user-action/{realmName}/{username}/remove")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response removeRequiredAction(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @QueryParam("action") String action) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);
        headers.put(KeycloakConstants.REQUIRED_ACTION, action);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=removeRequiredAction",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/user-action/{realmName}/{username}/execute")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response executeActionsEmail(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username,
            @QueryParam("actions") String actions,
            @QueryParam("redirectUri") String redirectUri,
            @QueryParam("lifespan") Integer lifespan) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        // Parse actions (comma-separated list)
        if (actions != null && !actions.isEmpty()) {
            List<String> actionList = List.of(actions.split(","));
            headers.put(KeycloakConstants.ACTIONS, actionList);
        }

        if (redirectUri != null) {
            headers.put(KeycloakConstants.REDIRECT_URI, redirectUri);
        }

        if (lifespan != null) {
            headers.put(KeycloakConstants.LIFESPAN, lifespan);
        }

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=executeActionsEmail",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== User Role Query Operations ====================

    @Path("/user-role/{realmName}/{username}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserRoles(
            @PathParam("realmName") String realmName,
            @PathParam("username") String username) {

        // First, get the user ID by username
        String userId = getUserIdByUsername(realmName, username);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.USER_ID, userId);

        @SuppressWarnings("unchecked")
        List<RoleRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getUserRoles",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    // ==================== Client Secret Operations ====================

    @Path("/client-secret/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClientSecret(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        CredentialRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getClientSecret",
                null,
                headers,
                CredentialRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/client-secret/{realmName}/{clientId}/regenerate")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response regenerateClientSecret(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        CredentialRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=regenerateClientSecret",
                null,
                headers,
                CredentialRepresentation.class);

        return Response.ok(result).build();
    }

    // ==================== Identity Provider Operations ====================

    @Path("/identity-provider/{realmName}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createIdentityProvider(
            @PathParam("realmName") String realmName,
            IdentityProviderRepresentation idp) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createIdentityProvider&pojoRequest=true",
                idp,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Identity provider created successfully")
                    .build();
        }

        return Response.ok("Identity provider created successfully").build();
    }

    @Path("/identity-provider/{realmName}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listIdentityProviders(@PathParam("realmName") String realmName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);

        @SuppressWarnings("unchecked")
        List<IdentityProviderRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listIdentityProviders",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/identity-provider/{realmName}/{idpAlias}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityProvider(
            @PathParam("realmName") String realmName,
            @PathParam("idpAlias") String idpAlias) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.IDP_ALIAS, idpAlias);

        IdentityProviderRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getIdentityProvider",
                null,
                headers,
                IdentityProviderRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/identity-provider/{realmName}/{idpAlias}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateIdentityProvider(
            @PathParam("realmName") String realmName,
            @PathParam("idpAlias") String idpAlias,
            IdentityProviderRepresentation idp) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.IDP_ALIAS, idpAlias);

        // Set the alias in the idp object
        idp.setAlias(idpAlias);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateIdentityProvider&pojoRequest=true",
                idp,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/identity-provider/{realmName}/{idpAlias}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteIdentityProvider(
            @PathParam("realmName") String realmName,
            @PathParam("idpAlias") String idpAlias) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.IDP_ALIAS, idpAlias);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteIdentityProvider",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Authorization Services - Resource Operations ====================

    @Path("/resource/{realmName}/{clientId}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createResource(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            ResourceRepresentation resource) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createResource&pojoRequest=true",
                resource,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Authorization resource created successfully")
                    .build();
        }

        return Response.ok("Authorization resource created successfully").build();
    }

    @Path("/resource/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listResources(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        @SuppressWarnings("unchecked")
        List<ResourceRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listResources",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/resource/{realmName}/{clientId}/{resourceId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResource(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("resourceId") String resourceId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.RESOURCE_ID, resourceId);

        ResourceRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getResource",
                null,
                headers,
                ResourceRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/resource/{realmName}/{clientId}/{resourceId}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateResource(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("resourceId") String resourceId,
            ResourceRepresentation resource) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.RESOURCE_ID, resourceId);

        // Set the ID in the resource object
        resource.setId(resourceId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateResource&pojoRequest=true",
                resource,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/resource/{realmName}/{clientId}/{resourceId}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteResource(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("resourceId") String resourceId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.RESOURCE_ID, resourceId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteResource",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Authorization Services - Policy Operations ====================

    @Path("/resource-policy/{realmName}/{clientId}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createResourcePolicy(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            PolicyRepresentation policy) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createResourcePolicy&pojoRequest=true",
                policy,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Authorization policy created successfully")
                    .build();
        }

        return Response.ok("Authorization policy created successfully").build();
    }

    @Path("/resource-policy/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listResourcePolicies(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        @SuppressWarnings("unchecked")
        List<PolicyRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listResourcePolicies",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/resource-policy/{realmName}/{clientId}/{policyId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResourcePolicy(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("policyId") String policyId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.POLICY_ID, policyId);

        PolicyRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getResourcePolicy",
                null,
                headers,
                PolicyRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/resource-policy/{realmName}/{clientId}/{policyId}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateResourcePolicy(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("policyId") String policyId,
            PolicyRepresentation policy) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.POLICY_ID, policyId);

        // Set the ID in the policy object
        policy.setId(policyId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateResourcePolicy&pojoRequest=true",
                policy,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/resource-policy/{realmName}/{clientId}/{policyId}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteResourcePolicy(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("policyId") String policyId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.POLICY_ID, policyId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteResourcePolicy",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Authorization Services - Permission Operations ====================

    @Path("/resource-permission/{realmName}/{clientId}/pojo")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response createResourcePermission(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            ResourcePermissionRepresentation permission) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        Object result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=createResourcePermission&pojoRequest=true",
                permission,
                headers);

        if (result instanceof jakarta.ws.rs.core.Response) {
            jakarta.ws.rs.core.Response jaxrsResponse = (jakarta.ws.rs.core.Response) result;
            return Response.status(jaxrsResponse.getStatus())
                    .entity("Authorization permission created successfully")
                    .build();
        }

        return Response.ok("Authorization permission created successfully").build();
    }

    @Path("/resource-permission/{realmName}/{clientId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listResourcePermissions(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);

        @SuppressWarnings("unchecked")
        List<ResourcePermissionRepresentation> result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=listResourcePermissions",
                null,
                headers,
                List.class);

        return Response.ok(result).build();
    }

    @Path("/resource-permission/{realmName}/{clientId}/{permissionId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResourcePermission(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("permissionId") String permissionId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.PERMISSION_ID, permissionId);

        PolicyRepresentation result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=getResourcePermission",
                null,
                headers,
                PolicyRepresentation.class);

        return Response.ok(result).build();
    }

    @Path("/resource-permission/{realmName}/{clientId}/{permissionId}")
    @jakarta.ws.rs.PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Response updateResourcePermission(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("permissionId") String permissionId,
            PolicyRepresentation permission) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.PERMISSION_ID, permissionId);

        // Set the ID in the permission object
        permission.setId(permissionId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=updateResourcePermission&pojoRequest=true",
                permission,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    @Path("/resource-permission/{realmName}/{clientId}/{permissionId}")
    @DELETE
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteResourcePermission(
            @PathParam("realmName") String realmName,
            @PathParam("clientId") String clientId,
            @PathParam("permissionId") String permissionId) {

        // First, get the client UUID by clientId
        String clientUuid = getClientUuidByClientId(realmName, clientId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KeycloakConstants.REALM_NAME, realmName);
        headers.put(KeycloakConstants.CLIENT_UUID, clientUuid);
        headers.put(KeycloakConstants.PERMISSION_ID, permissionId);

        String result = producerTemplate.requestBodyAndHeaders(
                getKeycloakEndpoint() + "&operation=deleteResourcePermission",
                null,
                headers,
                String.class);

        return Response.ok(result).build();
    }

    // ==================== Consumer Operations ====================

    @Path("/events/admin/{realmName}/enable")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response enableAdminEvents(@PathParam("realmName") String realmName) {
        try {
            // Get the realm representation
            Map<String, Object> headers = new HashMap<>();
            headers.put(KeycloakConstants.REALM_NAME, realmName);

            RealmRepresentation realm = producerTemplate.requestBodyAndHeaders(
                    getKeycloakEndpoint() + "&operation=getRealm",
                    null,
                    headers,
                    RealmRepresentation.class);

            // Enable admin events
            realm.setAdminEventsEnabled(true);
            realm.setAdminEventsDetailsEnabled(true);
            realm.setEventsEnabled(true);

            // Update the realm
            producerTemplate.requestBodyAndHeaders(
                    getKeycloakEndpoint() + "&operation=updateRealm&pojoRequest=true",
                    realm,
                    headers,
                    String.class);

            return Response.ok("Admin events enabled successfully").build();
        } catch (Exception e) {
            LOG.error("Failed to enable admin events", e);
            return Response.status(500).entity("Failed to enable admin events: " + e.getMessage()).build();
        }
    }

    @Path("/events/admin/collected")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCollectedAdminEvents() {
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:admin-events", MockEndpoint.class);
            List<AdminEventRepresentation> events = new ArrayList<>();

            mockEndpoint.getExchanges().forEach(exchange -> {
                Object body = exchange.getIn().getBody();
                if (body instanceof AdminEventRepresentation) {
                    events.add((AdminEventRepresentation) body);
                }
            });

            return Response.ok(events).build();
        } catch (Exception e) {
            LOG.error("Failed to get collected admin events", e);
            return Response.status(500).entity("Failed to get admin events: " + e.getMessage()).build();
        }
    }

    @Path("/events/regular/collected")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCollectedRegularEvents() {
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:events", MockEndpoint.class);
            List<EventRepresentation> events = new ArrayList<>();

            mockEndpoint.getExchanges().forEach(exchange -> {
                Object body = exchange.getIn().getBody();
                if (body instanceof EventRepresentation) {
                    events.add((EventRepresentation) body);
                }
            });

            return Response.ok(events).build();
        } catch (Exception e) {
            LOG.error("Failed to get collected regular events", e);
            return Response.status(500).entity("Failed to get regular events: " + e.getMessage()).build();
        }
    }

    @Path("/events/admin/reset")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response resetAdminEventsMock() {
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:admin-events", MockEndpoint.class);
            mockEndpoint.reset();
            return Response.ok("Admin events mock reset successfully").build();
        } catch (Exception e) {
            LOG.error("Failed to reset admin events mock", e);
            return Response.status(500).entity("Failed to reset mock: " + e.getMessage()).build();
        }
    }

    @Path("/events/regular/reset")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response resetRegularEventsMock() {
        try {
            MockEndpoint mockEndpoint = context.getEndpoint("mock:events", MockEndpoint.class);
            mockEndpoint.reset();
            return Response.ok("Regular events mock reset successfully").build();
        } catch (Exception e) {
            LOG.error("Failed to reset regular events mock", e);
            return Response.status(500).entity("Failed to reset mock: " + e.getMessage()).build();
        }
    }

    @Path("/consumer/admin-events/start/{realmName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response startAdminEventsConsumer(@PathParam("realmName") String realmName) {
        try {
            context.getRouteController().startRoute("admin-events-consumer-" + realmName);
            return Response.ok("Admin events consumer started").build();
        } catch (Exception e) {
            LOG.error("Failed to start admin events consumer", e);
            return Response.status(500).entity("Failed to start consumer: " + e.getMessage()).build();
        }
    }

    @Path("/consumer/regular-events/start/{realmName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response startRegularEventsConsumer(@PathParam("realmName") String realmName) {
        try {
            context.getRouteController().startRoute("regular-events-consumer-" + realmName);
            return Response.ok("Regular events consumer started").build();
        } catch (Exception e) {
            LOG.error("Failed to start regular events consumer", e);
            return Response.status(500).entity("Failed to start consumer: " + e.getMessage()).build();
        }
    }

    @Path("/consumer/route/create/{realmName}")
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response createConsumerRoutes(@PathParam("realmName") String realmName) {
        try {
            // Create admin events consumer route
            context.addRoutes(new org.apache.camel.builder.RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("keycloak:adminEvents"
                            + "?serverUrl=" + keycloakUrl
                            + "&authRealm=" + keycloakRealm
                            + "&username=" + keycloakUsername
                            + "&password=" + keycloakPassword
                            + "&realm=" + realmName
                            + "&eventType=admin-events"
                            + "&maxResults=50"
                            + "&initialDelay=500"
                            + "&delay=1000")
                            .autoStartup(false)
                            .routeId("admin-events-consumer-" + realmName)
                            .to("mock:admin-events");

                    // Create regular events consumer route
                    from("keycloak:events"
                            + "?serverUrl=" + keycloakUrl
                            + "&authRealm=" + keycloakRealm
                            + "&username=" + keycloakUsername
                            + "&password=" + keycloakPassword
                            + "&realm=" + realmName
                            + "&eventType=events"
                            + "&maxResults=50"
                            + "&initialDelay=500"
                            + "&delay=1000")
                            .autoStartup(false)
                            .routeId("regular-events-consumer-" + realmName)
                            .to("mock:events");
                }
            });

            return Response.ok("Consumer routes created successfully").build();
        } catch (Exception e) {
            LOG.error("Failed to create consumer routes", e);
            return Response.status(500).entity("Failed to create routes: " + e.getMessage()).build();
        }
    }
}
