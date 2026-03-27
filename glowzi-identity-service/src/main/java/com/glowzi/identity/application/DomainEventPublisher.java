package com.glowzi.identity.application;

import com.glowzi.identity.domain.event.DomainEvent;

import java.util.List;

/**
 * Port interface for publishing domain events.
 *
 * Defined in application layer — implementations live in infrastructure.
 * This keeps the domain and application layers free of Spring dependencies.
 */
public interface DomainEventPublisher {

    /**
     * Publish a single domain event to interested listeners.
     */
    void publish(DomainEvent event);

    /**
     * Publish all collected domain events from an aggregate.
     */
    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
    }
}
