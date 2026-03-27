package com.glowzi.identity.domain.vo;

/**
 * Value Object wrapping a full name string.
 *
 * Enforces the business rule: a name must be non-blank and ≤ 100 characters.
 * Because it's a record, it is immutable and equality is by value.
 */
public record FullName(String value) {

    private static final int MAX_LENGTH = 100;

    public FullName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Full name must not exceed " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
