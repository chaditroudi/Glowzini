package com.glowzi.identity.domain;

import com.glowzi.identity.domain.vo.Phone;

import java.util.Optional;

/**
 * Repository port — defined by the domain, implemented by infrastructure.
 *
 * In DDD, the domain OWNS this interface. The infrastructure ADAPTS to it.
 * Notice: methods use domain types (User, Phone), never JPA entities.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findByPhone(Phone phone);

    boolean existsByPhone(Phone phone);
}
