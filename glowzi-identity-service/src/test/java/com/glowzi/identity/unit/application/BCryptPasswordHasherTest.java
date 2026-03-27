package com.glowzi.identity.unit.application;

import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.infrastructure.security.BCryptPasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BCryptPasswordHasher — hashing and matching.
 */
class BCryptPasswordHasherTest {

    private BCryptPasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new BCryptPasswordHasher();
    }

    @Test
    @DisplayName("hash returns a non-null HashedPassword")
    void hash_returnsNonNull() {
        HashedPassword hashed = hasher.hash("MyPassword123");

        assertThat(hashed).isNotNull();
        assertThat(hashed.value()).isNotBlank();
    }

    @Test
    @DisplayName("hash produces BCrypt format string")
    void hash_producesBCryptFormat() {
        HashedPassword hashed = hasher.hash("MyPassword123");

        assertThat(hashed.value()).startsWith("$2a$");
    }

    @Test
    @DisplayName("matches returns true for correct password")
    void matches_correctPassword_returnsTrue() {
        HashedPassword hashed = hasher.hash("CorrectPassword");

        boolean result = hasher.matches("CorrectPassword", hashed.value());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("matches returns false for wrong password")
    void matches_wrongPassword_returnsFalse() {
        HashedPassword hashed = hasher.hash("CorrectPassword");

        boolean result = hasher.matches("WrongPassword", hashed.value());

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("hashing same password twice produces different hashes (salted)")
    void hash_samePasswordTwice_differentHashes() {
        HashedPassword hash1 = hasher.hash("SamePassword");
        HashedPassword hash2 = hasher.hash("SamePassword");

        assertThat(hash1.value()).isNotEqualTo(hash2.value());
    }
}
