package com.glowzi.identity.domain.vo;

/**
 * Value Object representing a validated phone number in E.164 format.
 *
 * In DDD, a Value Object is an immutable object defined by its attributes,
 * not by an identity. Two Phone objects with the same value are equal.
 *
 * Self-validating: it is impossible to create an invalid Phone instance.
 */
public record Phone(String value) {

    private static final String E164_PATTERN = "^\\+[1-9]\\d{6,14}$";

    public Phone {
        if (value == null || !value.matches(E164_PATTERN)) {
            throw new IllegalArgumentException(
                    "Phone must be in E.164 format (e.g. +966501234567), got: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
