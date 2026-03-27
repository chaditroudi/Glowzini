package com.glowzi.identity.integration;

import com.glowzi.identity.TestcontainersConfiguration;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import com.glowzi.identity.infrastructure.persistence.JpaUserRepository;
import com.glowzi.identity.infrastructure.persistence.UserRepositoryAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the UserRepositoryAdapter persistence mapping
 * using a real PostgreSQL via Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserRepositoryAdapter.class})
class UserRepositoryAdapterTest {

    @Autowired
    private UserRepositoryAdapter userRepositoryAdapter;

    @Autowired
    private JpaUserRepository jpaUserRepository;

    @BeforeEach
    void cleanUp() {
        jpaUserRepository.deleteAll();
    }

    @Test
    @DisplayName("save persists user and returns it with generated ID")
    void save_persistsAndReturnsWithId() {
        User user = User.register(
                new FullName("Test User"),
                new Phone("+966500000010"),
                new HashedPassword("$2a$10$hashedvalue123456789012345678901234567890123456"),
                UserRole.CUSTOMER,
                "en"
        );

        User saved = userRepositoryAdapter.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFullName().value()).isEqualTo("Test User");
        assertThat(saved.getPhone().value()).isEqualTo("+966500000010");
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getPreferredLanguage()).isEqualTo("en");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByPhone returns user when exists")
    void findByPhone_existingUser_returnsUser() {
        User user = User.register(
                new FullName("Lookup User"),
                new Phone("+966500000011"),
                new HashedPassword("$2a$10$hashedvalue123456789012345678901234567890123456"),
                UserRole.PROVIDER,
                "ar"
        );
        userRepositoryAdapter.save(user);

        Optional<User> found = userRepositoryAdapter.findByPhone(new Phone("+966500000011"));

        assertThat(found).isPresent();
        assertThat(found.get().getFullName().value()).isEqualTo("Lookup User");
        assertThat(found.get().getRole()).isEqualTo(UserRole.PROVIDER);
    }

    @Test
    @DisplayName("findByPhone returns empty when not exists")
    void findByPhone_nonExistent_returnsEmpty() {
        Optional<User> found = userRepositoryAdapter.findByPhone(new Phone("+966500000099"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByPhone returns true when phone exists")
    void existsByPhone_existingPhone_returnsTrue() {
        User user = User.register(
                new FullName("Exists User"),
                new Phone("+966500000012"),
                new HashedPassword("$2a$10$hashedvalue123456789012345678901234567890123456"),
                UserRole.CUSTOMER,
                null
        );
        userRepositoryAdapter.save(user);

        boolean exists = userRepositoryAdapter.existsByPhone(new Phone("+966500000012"));

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByPhone returns false when phone not exists")
    void existsByPhone_nonExistent_returnsFalse() {
        boolean exists = userRepositoryAdapter.existsByPhone(new Phone("+966500000098"));

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("domain-to-entity-to-domain mapping preserves all fields")
    void roundTrip_preservesAllFields() {
        User original = User.register(
                new FullName("Round Trip User"),
                new Phone("+966500000013"),
                new HashedPassword("$2a$10$hashedvalue123456789012345678901234567890123456"),
                UserRole.ADMIN,
                "fr"
        );

        User saved = userRepositoryAdapter.save(original);
        User loaded = userRepositoryAdapter.findByPhone(new Phone("+966500000013")).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getFullName()).isEqualTo(saved.getFullName());
        assertThat(loaded.getPhone()).isEqualTo(saved.getPhone());
        assertThat(loaded.getPasswordHash()).isEqualTo(saved.getPasswordHash());
        assertThat(loaded.getRole()).isEqualTo(saved.getRole());
        assertThat(loaded.getPreferredLanguage()).isEqualTo(saved.getPreferredLanguage());
    }
}
