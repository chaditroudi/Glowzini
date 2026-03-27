package com.glowzi.identity.application.command;

/**
 * Command object for user login.
 */
public record LoginCommand(
        String phone,
        String rawPassword
) {
}
