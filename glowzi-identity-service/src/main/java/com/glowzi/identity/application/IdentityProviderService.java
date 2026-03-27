package com.glowzi.identity.application;

/**
 * Port interface for external identity provider operations.
 * Application layer depends on this port; infrastructure layer implements it.
 * This preserves hexagonal architecture: application → port ← infrastructure.
 */
public interface IdentityProviderService {

    /**
     * Creates a user in the external identity provider.
     * @return the provider-assigned user ID
     */
    String createUser(String username, String fullName, String phone,
                      String rawPassword, String roleName);

    /**
     * Authenticates a user and returns an access token.
     * @throws IdentityProviderAuthException if credentials are invalid
     * @throws IdentityProviderException for other provider errors
     */
    String authenticate(String username, String password);

    // ─── Provider-specific exceptions (application-layer) ────────────

    class IdentityProviderException extends RuntimeException {
        public IdentityProviderException(String message) { super(message); }
        public IdentityProviderException(String message, Throwable cause) { super(message, cause); }
    }

    class IdentityProviderConflictException extends IdentityProviderException {
        public IdentityProviderConflictException(String message) { super(message); }
    }

    class IdentityProviderAuthException extends IdentityProviderException {
        public IdentityProviderAuthException(String message) { super(message); }
        public IdentityProviderAuthException(String message, Throwable cause) { super(message, cause); }
    }
}
