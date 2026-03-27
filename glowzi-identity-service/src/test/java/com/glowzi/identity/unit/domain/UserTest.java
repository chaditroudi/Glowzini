package com.glowzi.identity.unit.domain;

import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.event.DomainEvent;
import com.glowzi.identity.domain.event.UserRegisteredEvent;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the User Aggregate Root.
 * Tests business behavior, not just getters.
 */
class UserTest {

    private static final FullName VALID_NAME = new FullName("Ahmed Ali");
    private static final Phone VALID_PHONE = new Phone("+966501234567");
    private static final HashedPassword VALID_HASH = new HashedPassword("$2a$10$hashedvalue");

    @Test
    void register_should_create_user_with_correct_fields() {
        User user = User.register(VALID_NAME, VALID_PHONE, VALID_HASH,
                UserRole.CUSTOMER, "ar");

        assertThat(user.getId()).isNull();               // new entity — no DB id yet
        assertThat(user.getFullName()).isEqualTo(VALID_NAME);
        assertThat(user.getPhone()).isEqualTo(VALID_PHONE);
        assertThat(user.getPasswordHash()).isEqualTo(VALID_HASH);
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getPreferredLanguage()).isEqualTo("ar");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void register_should_emit_UserRegisteredEvent() {
        User user = User.register(VALID_NAME, VALID_PHONE, VALID_HASH,
                UserRole.PROVIDER, null);

        List<DomainEvent> events = user.getDomainEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(UserRegisteredEvent.class);

        UserRegisteredEvent event = (UserRegisteredEvent) events.get(0);
        assertThat(event.phone()).isEqualTo("+966501234567");
        assertThat(event.role()).isEqualTo(UserRole.PROVIDER);
    }

    @Test
    void clearDomainEvents_should_empty_the_list() {
        User user = User.register(VALID_NAME, VALID_PHONE, VALID_HASH,
                UserRole.CUSTOMER, null);

        user.clearDomainEvents();

        assertThat(user.getDomainEvents()).isEmpty();
    }

    @Test
    void register_should_reject_null_role() {
        assertThatThrownBy(() ->
                User.register(VALID_NAME, VALID_PHONE, VALID_HASH, null, "ar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role");
    }

    @Test
    void passwordMatches_should_return_true_when_checker_confirms() {
        User user = User.register(VALID_NAME, VALID_PHONE, VALID_HASH,
                UserRole.CUSTOMER, null);

        boolean result = user.passwordMatches("rawPass",
                (raw, hashed) -> raw.equals("rawPass") && hashed.equals(VALID_HASH.value()));

        assertThat(result).isTrue();
    }

    @Test
    void passwordMatches_should_return_false_when_checker_rejects() {
        User user = User.register(VALID_NAME, VALID_PHONE, VALID_HASH,
                UserRole.CUSTOMER, null);

        boolean result = user.passwordMatches("wrongPass",
                (raw, hashed) -> false);

        assertThat(result).isFalse();
    }
}
