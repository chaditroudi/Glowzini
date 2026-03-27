package com.glowzi.identity.infrastructure.security;

import com.glowzi.identity.application.IdentityProviderService;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Infrastructure adapter for Keycloak Admin REST API.
 * Creates users, assigns roles, and obtains tokens.
 */
@Component
public class KeycloakUserService implements IdentityProviderService {

    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final String adminUsername;
    private final String adminPassword;

    public KeycloakUserService(
            @Value("${keycloak.base-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret,
            @Value("${keycloak.admin.username}") String adminUsername,
            @Value("${keycloak.admin.password}") String adminPassword
    ) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    /**
     * Creates a user in Keycloak and assigns them a realm role.
     * Returns the Keycloak user ID (UUID string).
     */
    @Override
    public String createUser(String username, String fullName, String phone,
                             String rawPassword, String roleName) {
        try (Keycloak adminClient = buildAdminClient()) {
            RealmResource realmResource = adminClient.realm(realm);
            UsersResource usersResource = realmResource.users();

            // Build user representation
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);
            user.setFirstName(fullName);
            user.setEnabled(true);
            user.setAttributes(Map.of("phone", List.of(phone)));

            // Set password credential
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(rawPassword);
            credential.setTemporary(false);
            user.setCredentials(Collections.singletonList(credential));

            // Create user
            try (Response response = usersResource.create(user)) {
                if (response.getStatus() == 409) {
                    throw new KeycloakConflictException("User already exists in Keycloak: " + username);
                }
                if (response.getStatus() != 201) {
                    throw new KeycloakException("Failed to create user in Keycloak. Status: "
                            + response.getStatus());
                }
            }

            // Get created user ID
            List<UserRepresentation> createdUsers = usersResource.searchByUsername(username, true);
            if (createdUsers.isEmpty()) {
                throw new KeycloakException("User created but not found: " + username);
            }
            String keycloakUserId = createdUsers.get(0).getId();

            // Assign realm role
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            usersResource.get(keycloakUserId).roles().realmLevel()
                    .add(Collections.singletonList(role));

            return keycloakUserId;
        }
    }

    /**
     * Implements the port's authenticate method — returns just the access token string.
     */
    @Override
    public String authenticate(String username, String password) {
        return getToken(username, password).getToken();
    }

    /**
     * Obtains an access token from Keycloak using Resource Owner Password Credentials grant.
     */
    public AccessTokenResponse getToken(String username, String password) {
        try (Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(username)
                .password(password)
                .grantType("password")
                .build()) {

            return keycloak.tokenManager().getAccessToken();
        } catch (Exception e) {
            throw new KeycloakAuthException("Authentication failed", e);
        }
    }

    private Keycloak buildAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .grantType("password")
                .build();
    }

    // ─── Keycloak-specific exceptions (extend port exceptions) ─────

    public static class KeycloakException extends IdentityProviderException {
        public KeycloakException(String message) { super(message); }
        public KeycloakException(String message, Throwable cause) { super(message, cause); }
    }

    public static class KeycloakConflictException extends IdentityProviderConflictException {
        public KeycloakConflictException(String message) { super(message); }
    }

    public static class KeycloakAuthException extends IdentityProviderAuthException {
        public KeycloakAuthException(String message, Throwable cause) { super(message, cause); }
    }
}
