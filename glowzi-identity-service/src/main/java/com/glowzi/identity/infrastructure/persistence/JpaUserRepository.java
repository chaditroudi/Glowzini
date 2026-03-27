package com.glowzi.identity.infrastructure.persistence;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByPhone(String phone);
    boolean existsByPhone(String phone);

}
