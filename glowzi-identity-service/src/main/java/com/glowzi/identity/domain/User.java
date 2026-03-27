package com.glowzi.identity.domain;

import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.event.DomainEvent;
import com.glowzi.identity.domain.event.UserRegisteredEvent;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User — the Aggregate Root of the Identity bounded context.
 *
 * DDD RULES ENFORCED HERE:
 * 1. All state changes go through behavior methods (no public setters).
 * 2. Business invariants are validated inside the aggregate.
 * 3. Domain events are collected and can be published after persistence.
 * 4. Fields use Value Objects, not raw primitives.
 */
public class User {

    private Long id;
    private FullName fullName;
    private Phone phone;
    private HashedPassword passwordHash;
    private UserRole role;
    private String preferredLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Domain events collected during this aggregate's lifecycle. */
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // ─── Constructor (used only for reconstitution from DB) ───────────
    public User(Long id, FullName fullName, Phone phone, HashedPassword passwordHash,
                UserRole role, String preferredLanguage,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.preferredLanguage = preferredLanguage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ─── Factory method: the ONLY way to create a new user ───────────
    /**
     * Creates a new User aggregate and emits a UserRegisteredEvent.
     *
     * Business rules enforced:
     * - FullName, Phone, HashedPassword are validated via their Value Objects
     * - Role must not be null
     * - Timestamps are set automatically
     */
    public static User register(FullName fullName,
                                Phone phone,
                                HashedPassword passwordHash,
                                UserRole role,
                                String preferredLanguage) {
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User(null, fullName, phone, passwordHash, role,
                preferredLanguage, now, now);

        // Collect domain event — will be published after persistence
        user.domainEvents.add(new UserRegisteredEvent(
                null, phone.value(), role, now));

        return user;
    }

    // ─── Behavior methods ────────────────────────────────────────────

    /**
     * Checks whether the given raw password matches this user's stored hash.
     * Returns true/false — the caller (use case) decides what to do.
     *
     * Why a method on User and not in the use case?
     * → Because "does this password belong to this user?" is a domain concern.
     */
    public boolean passwordMatches(String rawPassword, PasswordMatchChecker checker) {
        return checker.matches(rawPassword, this.passwordHash.value());
    }

    // ─── Domain Events ───────────────────────────────────────────────

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // ─── Getters (read-only access to state) ─────────────────────────

    public Long getId() { return id; }
    public FullName getFullName() { return fullName; }
    public Phone getPhone() { return phone; }
    public HashedPassword getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * Functional interface used by the aggregate to verify passwords
     * without depending on the application-layer PasswordHasher.
     *
     * The use case passes a lambda: user.passwordMatches(raw, hasher::matches)
     */
    @FunctionalInterface
    public interface PasswordMatchChecker {
        boolean matches(String rawPassword, String hashedPassword);
    }
}
