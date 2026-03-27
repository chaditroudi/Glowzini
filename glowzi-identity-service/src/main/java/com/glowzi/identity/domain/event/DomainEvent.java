package com.glowzi.identity.domain.event;

/**
 * Marker interface for all domain events.
 *
 * Domain events capture "something that happened" in the domain.
 * They are collected inside the aggregate and published AFTER persistence.
 */
public interface DomainEvent {
}
