package com.glowzi.identity.application;

import com.glowzi.identity.domain.User;

public interface JwtService {
    String generateToken(User user);
}
