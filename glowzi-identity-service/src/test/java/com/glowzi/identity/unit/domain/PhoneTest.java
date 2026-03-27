package com.glowzi.identity.unit.domain;

import com.glowzi.identity.domain.vo.Phone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Phone Value Object.
 * Pure JUnit 5 + AssertJ — no Spring context needed.
 */
class PhoneTest {

    @Test
    void should_create_valid_phone() {
        Phone phone = new Phone("+966501234567");
        assertThat(phone.value()).isEqualTo("+966501234567");
    }

    @Test
    void should_reject_null() {
        assertThatThrownBy(() -> new Phone(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void should_reject_empty_string() {
        assertThatThrownBy(() -> new Phone(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_without_plus() {
        assertThatThrownBy(() -> new Phone("966501234567"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_too_short() {
        assertThatThrownBy(() -> new Phone("+12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_minimum_length() {
        // +X followed by 6 digits = 7 digits total after +
        Phone phone = new Phone("+1234567");
        assertThat(phone.value()).isEqualTo("+1234567");
    }

    @Test
    void two_phones_with_same_value_are_equal() {
        Phone a = new Phone("+966501234567");
        Phone b = new Phone("+966501234567");
        assertThat(a).isEqualTo(b);
    }
}
