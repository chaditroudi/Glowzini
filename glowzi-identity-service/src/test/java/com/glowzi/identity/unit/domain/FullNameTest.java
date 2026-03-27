package com.glowzi.identity.unit.domain;

import com.glowzi.identity.domain.vo.FullName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FullNameTest {

    @Test
    void should_create_valid_name() {
        FullName name = new FullName("Ahmed Ali");
        assertThat(name.value()).isEqualTo("Ahmed Ali");
    }

    @Test
    void should_reject_null() {
        assertThatThrownBy(() -> new FullName(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void should_reject_blank() {
        assertThatThrownBy(() -> new FullName("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_over_100_chars() {
        String longName = "A".repeat(101);
        assertThatThrownBy(() -> new FullName(longName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void should_accept_exactly_100_chars() {
        String name = "A".repeat(100);
        assertThat(new FullName(name).value()).hasSize(100);
    }

    @Test
    void two_names_with_same_value_are_equal() {
        assertThat(new FullName("Ahmed")).isEqualTo(new FullName("Ahmed"));
    }
}
