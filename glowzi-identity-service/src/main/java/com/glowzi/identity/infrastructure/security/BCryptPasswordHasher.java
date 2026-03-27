package com.glowzi.identity.infrastructure.security;

import com.glowzi.identity.application.PasswordHasher;
import com.glowzi.identity.domain.vo.HashedPassword;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public HashedPassword hash(String rawPassword) {
        return new HashedPassword(encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
