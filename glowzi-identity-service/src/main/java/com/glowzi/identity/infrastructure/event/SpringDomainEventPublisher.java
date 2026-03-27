package com.glowzi.identity.infrastructure.event;

import com.glowzi.identity.application.DomainEventPublisher;
import com.glowzi.identity.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that publishes domain events via Spring's
 * ApplicationEventPublisher.
 *
 * Any Spring bean can listen by annotating a method with @EventListener:
 *
 *   @EventListener
 *   public void onUserRegistered(UserRegisteredEvent event) { ... }
 */
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;

    public SpringDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
    }
}
