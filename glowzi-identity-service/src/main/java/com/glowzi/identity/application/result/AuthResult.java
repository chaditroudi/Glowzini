package com.glowzi.identity.application.result;

/**
 * Result object returned by authentication use cases.
 *
 * Lives in application/ — the controller maps this to an HTTP-specific DTO.
 * This way the use case never knows about HTTP, JSON, or REST.
 */
public record AuthResult(
        Long userId,
        String role,
        String token
) {
}
