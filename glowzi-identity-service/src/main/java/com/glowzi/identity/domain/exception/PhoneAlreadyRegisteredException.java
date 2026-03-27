package com.glowzi.identity.domain.exception;

/**
 * Thrown when a registration is attempted with a phone that already exists.
 */
public class PhoneAlreadyRegisteredException extends DomainException {

    public PhoneAlreadyRegisteredException(String phone) {
        super("Phone already registered: " + phone);
    }
}
