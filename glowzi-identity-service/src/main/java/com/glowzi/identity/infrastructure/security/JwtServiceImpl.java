package com.glowzi.identity.infrastructure.security;

import com.glowzi.identity.application.JwtService;
import com.glowzi.identity.domain.User;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.stereotype.Component;

/**
 * Delegates token generation to Keycloak.
 * The User's phone is used as the Keycloak username.
 */
@Component
public class JwtServiceImpl implements JwtService {

    private final KeycloakUserService keycloakUserService;

    public JwtServiceImpl(KeycloakUserService keycloakUserService) {
        this.keycloakUserService = keycloakUserService;
    }

    @Override
    public String generateToken(User user) {
        // This method is no longer the primary way to get tokens.
        // Tokens are obtained directly via KeycloakUserService.getToken() in the use cases.
        // Kept for port contract compatibility.
        throw new UnsupportedOperationException(
                "Use KeycloakUserService.getToken() directly. "
                + "Token generation is now handled by Keycloak.");
    }

    /**
     * Obtains a Keycloak access token using username + password.
     */
    public String getToken(String username, String password) {
        AccessTokenResponse tokenResponse = keycloakUserService.getToken(username, password);
        return tokenResponse.getToken();
    }
}
