package com.glowzi.identity.domain.event;

import com.glowzi.identity.domain.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Emitted when a new user successfully registers.
 *
 * Downstream listeners can send a welcome SMS, update analytics, etc.
 * — all decoupled from the registration logic itself.
 */
public record UserRegisteredEvent(
        Long userId,
        String phone,
        UserRole role,
        LocalDateTime registeredAt
) implements DomainEvent {
}
