package com.glowzi.identity.domain.exception;

/**
 * Thrown when login fails — deliberately vague to prevent phone enumeration.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
