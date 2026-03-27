package com.glowzi.identity.interfaces.rest;

import com.glowzi.identity.application.IdentityProviderService;
import com.glowzi.identity.domain.exception.DomainException;
import com.glowzi.identity.domain.exception.InvalidCredentialsException;
import com.glowzi.identity.domain.exception.PhoneAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps domain exceptions → HTTP status codes + JSON error bodies.
 * Each domain exception has its own semantically correct HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PhoneAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)                    // 409
    public Map<String, String> handlePhoneAlreadyRegistered(PhoneAlreadyRegisteredException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)                // 401
    public Map<String, String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)                 // 400 — catch-all for domain errors
    public Map<String, String> handleDomainException(DomainException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)                 // 400 — VO validation errors
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        body.put("fields", fieldErrors);

        return body;
    }

    @ExceptionHandler(IdentityProviderService.IdentityProviderConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)                    // 409
    public Map<String, String> handleProviderConflict(IdentityProviderService.IdentityProviderConflictException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IdentityProviderService.IdentityProviderAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)                // 401
    public Map<String, String> handleProviderAuth(IdentityProviderService.IdentityProviderAuthException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IdentityProviderService.IdentityProviderException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)         // 503
    public Map<String, String> handleProviderError(IdentityProviderService.IdentityProviderException ex) {
        return Map.of("error", "Identity provider error: " + ex.getMessage());
    }
}
