package com.glowzi.identity.infrastructure.persistence;

import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Adapter — bridges the domain UserRepository port to Spring Data JPA.
 *
 * Responsible for converting between domain objects (with VOs)
 * and JPA entities (with raw Strings). This is the "Anti-Corruption Layer"
 * that keeps JPA concerns out of the domain.
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity saved = jpaUserRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findByPhone(Phone phone) {
        return jpaUserRepository.findByPhone(phone.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByPhone(Phone phone) {
        return jpaUserRepository.existsByPhone(phone.value());
    }

    // ─── Mapping methods ─────────────────────────────────────────────

    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());                          // null for new
        entity.setFullName(user.getFullName().value());      // unwrap VO
        entity.setPhone(user.getPhone().value());            // unwrap VO
        entity.setPasswordHash(user.getPasswordHash().value()); // unwrap VO
        entity.setRole(user.getRole());
        entity.setPreferredLanguage(user.getPreferredLanguage());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                new FullName(entity.getFullName()),           // wrap in VO
                new Phone(entity.getPhone()),                 // wrap in VO
                new HashedPassword(entity.getPasswordHash()), // wrap in VO
                entity.getRole(),
                entity.getPreferredLanguage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
