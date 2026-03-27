package com.glowzi.identity.application;

import com.glowzi.identity.domain.vo.HashedPassword;

/**
 * Port interface for password hashing — defined by the application layer,
 * implemented by infrastructure (BCrypt adapter).
 */
public interface PasswordHasher {

    /** Hash a raw password and return a Value Object wrapping the hash. */
    HashedPassword hash(String rawPassword);

    /** Check if a raw password matches a previously hashed password. */
    boolean matches(String rawPassword, String hashedPassword);
}
