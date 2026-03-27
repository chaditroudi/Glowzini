package com.glowzi.identity.unit.domain;

import com.glowzi.identity.domain.vo.HashedPassword;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HashedPasswordTest {

    @Test
    void should_create_valid_hash() {
        HashedPassword hp = new HashedPassword("$2a$10$abcdef");
        assertThat(hp.value()).isEqualTo("$2a$10$abcdef");
    }

    @Test
    void should_reject_null() {
        assertThatThrownBy(() -> new HashedPassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_blank() {
        assertThatThrownBy(() -> new HashedPassword("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_should_mask_value() {
        HashedPassword hp = new HashedPassword("$2a$10$secret");
        assertThat(hp.toString()).doesNotContain("secret");
        assertThat(hp.toString()).contains("***");
    }
}
