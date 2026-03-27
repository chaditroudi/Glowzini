package com.glowzi.identity.domain.vo;

/**
 * Value Object wrapping an already-hashed password.
 *
 * This type makes it impossible to accidentally store a raw password:
 * if a method requires HashedPassword, the caller MUST hash first.
 */
public record HashedPassword(String value) {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Hashed password must not be blank");
        }
    }

    @Override
    public String toString() {
        // Never print the hash in logs
        return "HashedPassword[***]";
    }
}
