package com.glowzi.identity.domain.exception;

/**
 * Base class for all domain-level exceptions.
 *
 * In DDD, the domain defines its own error language.
 * Each exception represents a broken business rule — not a technical failure.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
