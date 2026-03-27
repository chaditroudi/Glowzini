package com.glowzi.identity.application.command;

/**
 * Command object for user registration.
 *
 * Commands live in the APPLICATION layer — not in interfaces/.
 * The controller maps its DTO → this command before calling the use case.
 *
 * This is what breaks the illegal dependency: application no longer
 * imports anything from interfaces/rest/dto/.
 */
public record RegisterUserCommand(
        String fullName,
        String phone,
        String rawPassword,
        String role,
        String preferredLanguage
) {
}
