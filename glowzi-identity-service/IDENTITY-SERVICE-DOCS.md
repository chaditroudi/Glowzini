# Glowzi Identity Service — Full Documentation

> **Module**: `glowzi-identity-service`  
> **Stack**: Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security · PostgreSQL · Flyway · JJWT 0.12.5  
> **Architecture**: Hexagonal (Ports & Adapters) / Clean Architecture  
> **Last updated**: 24 March 2026

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)  
2. [Package Structure](#2-package-structure)  
3. [Problems We Solved (Changelog)](#3-problems-we-solved-changelog)  
4. [All Code Files (Annotated)](#4-all-code-files-annotated)  
   - 4.1 [Domain Layer](#41-domain-layer)  
   - 4.2 [Application Layer](#42-application-layer)  
   - 4.3 [Infrastructure Layer](#43-infrastructure-layer)  
   - 4.4 [Interfaces Layer (REST API)](#44-interfaces-layer-rest-api)  
   - 4.5 [Configuration & Migrations](#45-configuration--migrations)  
5. [Best Practices Applied](#5-best-practices-applied)  
6. [Database Schema](#6-database-schema)  
7. [Dependencies (pom.xml)](#7-dependencies-pomxml)  
8. [How to Run](#8-how-to-run)  
9. [API Reference](#9-api-reference)  
10. [Future Improvements](#10-future-improvements)  

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      interfaces/rest                         │
│   AuthController  GlobalExceptionHandler  DTOs (Request/Res) │
├──────────────────────────────────────────────────────────────┤
│                       application                            │
│     RegisterUserUseCase   LoginUserUseCase                   │
│     PasswordHasher (port)   JwtService (port)                │
├──────────────────────────────────────────────────────────────┤
│                         domain                               │
│       User   UserRole   UserRepository (port)                │
├──────────────────────────────────────────────────────────────┤
│                      infrastructure                          │
│  persistence/                    security/                    │
│    UserEntity                      BCryptPasswordHasher       │
│    JpaUserRepository               JwtServiceImpl (jjwt)     │
│    UserRepositoryAdapter                                     │
└──────────────────────────────────────────────────────────────┘
         │                                │
         ▼                                ▼
    PostgreSQL                    HMAC-SHA256 JWT
    (Flyway-managed)              (externalized secret)
```

**Key Rule**: Dependencies point **inward**. The `domain` layer has zero Spring imports. The `application` layer defines port interfaces. The `infrastructure` layer provides concrete implementations annotated as Spring beans.

---

## 2. Package Structure

```
com.glowzi.identity/
│
├── GlowziIdentityServiceApplication.java        # Spring Boot entry point
│
├── domain/                                       # Pure Java — zero framework imports
│   ├── User.java                                 # Domain entity (immutable)
│   ├── UserRepository.java                       # Outbound port interface
│   └── enums/
│       └── UserRole.java                         # CUSTOMER, PROVIDER, ADMIN
│
├── application/                                  # Use cases & service port interfaces
│   ├── RegisterUserUseCase.java                  # @Service — user registration flow
│   ├── LoginUserUseCase.java                     # @Service — user login flow
│   ├── PasswordHasher.java                       # Outbound port interface
│   └── JwtService.java                           # Outbound port interface
│
├── infrastructure/                               # Adapters — all Spring-managed beans
│   ├── persistence/
│   │   ├── UserEntity.java                       # JPA @Entity mapped to "users" table
│   │   ├── JpaUserRepository.java                # Spring Data JpaRepository
│   │   └── UserRepositoryAdapter.java            # @Repository — bridges domain ↔ JPA
│   └── security/
│       ├── BCryptPasswordHasher.java             # @Component — implements PasswordHasher
│       └── JwtServiceImpl.java                   # @Component — implements JwtService (jjwt)
│
└── interfaces/
    └── rest/
        ├── AuthController.java                   # @RestController — /auth endpoints
        ├── GlobalExceptionHandler.java           # @RestControllerAdvice — error handling
        └── dto/
            ├── RegisterUserRequest.java          # Inbound DTO with Bean Validation
            ├── LoginRequest.java                 # Inbound DTO with Bean Validation
            └── AuthResponse.java                 # Outbound DTO (userId, role, token)
```

---

## 3. Problems We Solved (Changelog)

### Issue 1 — `Could not autowire. No beans of 'UserRepository' type found`

**Root cause**: `UserRepository` is a plain Java interface (a domain port) — not a Spring Data repository. The `infrastructure/` package was completely empty, so there were **zero beans** for `UserRepository`, `PasswordHasher`, or `JwtService`.

**Fix**: Created three adapter classes in `infrastructure/` annotated with `@Repository` / `@Component` that implement the domain and application port interfaces.

### Issue 2 — `Could not resolve placeholder 'app.jwt.secret'`

**Root cause**: `JwtServiceImpl` was upgraded to use `jjwt` library with `@Value("${app.jwt.secret}")` constructor injection, but the `app.jwt.*` properties were missing from `application.yml`.

**Fix**: Added `app.jwt.secret` and `app.jwt.expiration-minutes` to `application.yml`.

### Issue 3 — Database schema out of sync

**Root cause**: The domain `User` has `fullName`, `preferredLanguage`, and `updatedAt`, but the `V1__init.sql` migration only created `id`, `phone`, `password_hash`, `role`, `created_at`.

**Fix**: Added `V2__add_user_profile_columns.sql` migration.

### Issue 4 — Bogus field in `RegisterUserRequest`

**Root cause**: Had a field `String RegisterUserRequest` (same name as the class) with nonsensical getter/setter.

**Fix**: Replaced with `String preferredLanguage` field.

### Additional features added

| Feature | Files |
|---------|-------|
| **Login flow** | `LoginUserUseCase.java` |
| **REST endpoints** | `AuthController.java` — `POST /auth/register` & `POST /auth/login` |
| **Global error handling** | `GlobalExceptionHandler.java` — handles `IllegalArgumentException` & validation errors |
| **Production JWT** | `JwtServiceImpl.java` — uses `jjwt` library with externalized config |

---

## 4. All Code Files (Annotated)

### 4.1 Domain Layer

> **Rule**: Zero framework imports. Pure Java. Testable without Spring context.

#### `domain/User.java` — Domain Entity

```java
package com.glowzi.identity.domain;

import com.glowzi.identity.domain.enums.UserRole;
import java.time.LocalDateTime;

public class User {
    private Long id;
    private String fullName;
    private String phone;
    private String passwordHash;
    private UserRole role;
    private String preferredLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User(Long id, String fullName, String phone, String passwordHash,
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

    public static User create(String fullName, String phone, String passwordHash,
                              UserRole role, String preferredLanguage) {
        LocalDateTime now = LocalDateTime.now();
        return new User(null, fullName, phone, passwordHash, role, preferredLanguage, now, now);
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

**Best Practices**:
- ✅ No JPA annotations — this is a **domain entity**, not a persistence entity
- ✅ Static factory method `create()` — encapsulates creation logic, auto-sets timestamps
- ✅ Immutable (getters only, no setters) — protects domain invariants
- ✅ `id = null` for new entities — lets the DB generate the value via `BIGSERIAL`

---

#### `domain/UserRepository.java` — Outbound Port

```java
package com.glowzi.identity.domain;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findByPhone(String phone);
    boolean existByPhone(String phone);
}
```

**Best Practices**:
- ✅ Plain Java interface — defines the **contract** the domain needs
- ✅ Returns domain `User`, not a JPA entity — no persistence leakage
- ✅ Lives in `domain/` — the domain **owns** its ports

---

#### `domain/enums/UserRole.java` — Value Object

```java
package com.glowzi.identity.domain.enums;

public enum UserRole {
    CUSTOMER,
    PROVIDER,
    ADMIN
}
```

**Best Practices**:
- ✅ Enum restricts the set of valid roles at compile time
- ✅ Stored as `VARCHAR` in DB via `@Enumerated(EnumType.STRING)` — avoids ordinal fragility

---

### 4.2 Application Layer

> **Rule**: Orchestrates domain logic. Defines port interfaces for external services. Depends only on `domain/`.

#### `application/RegisterUserUseCase.java` — Registration Use Case

```java
package com.glowzi.identity.application;

import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.interfaces.rest.dto.AuthResponse;
import com.glowzi.identity.interfaces.rest.dto.RegisterUserRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public RegisterUserUseCase(UserRepository userRepository,
                               PasswordHasher passwordHasher,
                               JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse exectue(RegisterUserRequest request) {
        if (userRepository.existByPhone(request.getPhone())) {
            throw new IllegalArgumentException("Phone already registered");
        }

        User user = User.create(
                request.getFullName(),
                request.getPhone(),
                passwordHasher.hash(request.getPassword()),
                request.getRole(),
                request.getPreferredLanguage()
        );

        User saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        return new AuthResponse(saved.getId(), saved.getRole().name(), token);
    }
}
```

**Best Practices**:
- ✅ `@Transactional` — ensures atomicity (if JWT generation fails, the save rolls back)
- ✅ Constructor injection — no `@Autowired` needed with single constructor
- ✅ Depends on **interfaces** not concrete classes
- ✅ Business rule (phone uniqueness) lives in the use case, not the controller
- ✅ Password is hashed **before** creating the domain entity

---

#### `application/LoginUserUseCase.java` — Login Use Case

```java
package com.glowzi.identity.application;

import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.interfaces.rest.dto.AuthResponse;
import com.glowzi.identity.interfaces.rest.dto.LoginRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public LoginUserUseCase(UserRepository userRepository,
                            PasswordHasher passwordHasher,
                            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse execute(LoginRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordHasher.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(user.getId(), user.getRole().name(), token);
    }
}
```

**Best Practices**:
- ✅ Same generic error message for "user not found" and "wrong password" — prevents phone enumeration attacks
- ✅ Uses `PasswordHasher.matches()` — never compares raw passwords directly
- ✅ Returns a fresh JWT on each login — stateless authentication

---

#### `application/PasswordHasher.java` — Service Port

```java
package com.glowzi.identity.application;

public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
```

**Best Practices**:
- ✅ Abstracts the hashing algorithm — can swap BCrypt for Argon2id without touching use cases
- ✅ `matches()` needed for login; `hash()` needed for registration

---

#### `application/JwtService.java` — Service Port

```java
package com.glowzi.identity.application;

import com.glowzi.identity.domain.User;

public interface JwtService {
    String generateToken(User user);
}
```

**Best Practices**:
- ✅ Takes domain `User` — application layer speaks in domain terms
- ✅ Implementation details (secret, algorithm, expiry) hidden behind the port

---

### 4.3 Infrastructure Layer

> **Rule**: Implements all ports. Contains all framework-specific code (JPA, Spring Security, jjwt).

#### `infrastructure/persistence/UserEntity.java` — JPA Entity

```java
package com.glowzi.identity.infrastructure.persistence;

import com.glowzi.identity.domain.enums.UserRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Getters
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Setters (used by the adapter for domain → entity mapping)
    public void setId(Long id) { this.id = id; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setRole(UserRole role) { this.role = role; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

**Best Practices**:
- ✅ **Separate from domain entity** — JPA annotations stay in `infrastructure/`
- ✅ `@Enumerated(EnumType.STRING)` — stores role as `"CUSTOMER"`, not `0`
- ✅ `@GeneratedValue(IDENTITY)` — matches PostgreSQL `BIGSERIAL`
- ✅ Column names use `snake_case` to match DB convention (`full_name`, `password_hash`)

---

#### `infrastructure/persistence/JpaUserRepository.java` — Spring Data Interface

```java
package com.glowzi.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByPhone(String phone);
    boolean existsByPhone(String phone);
}
```

**Best Practices**:
- ✅ Extends `JpaRepository` — Spring Data auto-generates the implementation
- ✅ Method names follow query derivation convention (`findByPhone`, `existsByPhone`)
- ✅ Returns `Optional` — forces callers to handle the absent case
- ✅ Works with `UserEntity` (JPA), not domain `User` — stays inside infrastructure

---

#### `infrastructure/persistence/UserRepositoryAdapter.java` — Adapter (Port → JPA)

```java
package com.glowzi.identity.infrastructure.persistence;

import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = new UserEntity();
        entity.setFullName(user.getFullName());
        entity.setPhone(user.getPhone());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRole(user.getRole());
        entity.setPreferredLanguage(user.getPreferredLanguage());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());

        UserEntity saved = jpaUserRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        return jpaUserRepository.findByPhone(phone).map(this::toDomain);
    }

    @Override
    public boolean existByPhone(String phone) {
        return jpaUserRepository.existsByPhone(phone);
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getPreferredLanguage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
```

**Best Practices**:
- ✅ `@Repository` — registers as a Spring bean **and** enables JPA exception translation
- ✅ **This is the key class that solved the original autowiring error**
- ✅ Converts domain `User` ↔ JPA `UserEntity` at the boundary — no JPA leakage upstream
- ✅ Private `toDomain()` helper keeps mapping logic in one place
- ✅ Single Responsibility — only adapts; no business logic

---

#### `infrastructure/security/BCryptPasswordHasher.java` — Adapter

```java
package com.glowzi.identity.infrastructure.security;

import com.glowzi.identity.application.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        return encoder.matches(rawPassword, hashedPassword);
    }
}
```

**Best Practices**:
- ✅ Uses Spring Security's `BCryptPasswordEncoder` — industry standard, salted by default
- ✅ `@Component` makes it discoverable for `PasswordHasher` injection
- ✅ Reusable `encoder` instance — `BCryptPasswordEncoder` is thread-safe

---

#### `infrastructure/security/JwtServiceImpl.java` — Adapter (jjwt library)

```java
package com.glowzi.identity.infrastructure.security;

import com.glowzi.identity.application.JwtService;
import com.glowzi.identity.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class JwtServiceImpl implements JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    @Override
    public String generateToken(User user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("phone", user.getPhone())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}
```

**Best Practices**:
- ✅ Uses **jjwt 0.12.5** — production-grade JWT library (replaces the earlier hand-rolled HMAC)
- ✅ Secret and expiration are **externalized** via `@Value` — injected from `application.yml`
- ✅ `Keys.hmacShaKeyFor()` — ensures the key meets HMAC-SHA256 minimum length requirements
- ✅ Standard JWT claims: `sub` (user ID), `iat` (issued at), `exp` (expiration)
- ✅ Custom claims: `phone`, `role` — available for downstream services via the API gateway

---

### 4.4 Interfaces Layer (REST API)

> **Rule**: Handles HTTP concerns only — validation, serialization, status codes. Delegates to use cases.

#### `interfaces/rest/AuthController.java` — REST Controller

```java
package com.glowzi.identity.interfaces.rest;

import com.glowzi.identity.application.LoginUserUseCase;
import com.glowzi.identity.application.RegisterUserUseCase;
import com.glowzi.identity.interfaces.rest.dto.AuthResponse;
import com.glowzi.identity.interfaces.rest.dto.LoginRequest;
import com.glowzi.identity.interfaces.rest.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                          LoginUserUseCase loginUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return registerUserUseCase.exectue(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUserUseCase.execute(request);
    }
}
```

**Best Practices**:
- ✅ `@Valid` triggers Jakarta Bean Validation on request DTOs before entering the use case
- ✅ `@ResponseStatus(CREATED)` — returns 201 for resource creation (register) and login token issuance
- ✅ Thin controller — zero business logic, pure delegation to use cases
- ✅ Constructor injection — immutable dependencies

---

#### `interfaces/rest/GlobalExceptionHandler.java` — Error Handling

```java
package com.glowzi.identity.interfaces.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException ex) {
        return Map.of("error", "Validation failed");
    }
}
```

**Best Practices**:
- ✅ `@RestControllerAdvice` — centralizes all error handling in one place
- ✅ Returns consistent JSON error shape: `{"error": "message"}`
- ✅ Catches `IllegalArgumentException` (thrown by use cases) → 400 Bad Request
- ✅ Catches `MethodArgumentNotValidException` (thrown by `@Valid`) → 400 Bad Request

---

#### `interfaces/rest/dto/RegisterUserRequest.java` — Inbound DTO

```java
package com.glowzi.identity.interfaces.rest.dto;

import com.glowzi.identity.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegisterUserRequest {

    @NotBlank  private String fullName;
    @NotBlank  private String phone;
    @NotBlank  private String password;
    @NotNull   private UserRole role;
               private String preferredLanguage;   // optional

    // Getters & Setters ...
}
```

**Best Practices**:
- ✅ Jakarta Bean Validation annotations — validated automatically with `@Valid`
- ✅ Separate DTO — never expose domain entities in the API contract
- ✅ `preferredLanguage` is optional (no validation annotation)

---

#### `interfaces/rest/dto/LoginRequest.java` — Inbound DTO

```java
package com.glowzi.identity.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    @NotBlank  private String phone;
    @NotBlank  private String password;

    // Getters & Setters ...
}
```

**Best Practices**:
- ✅ Minimal — only the fields needed for authentication
- ✅ `@NotBlank` ensures non-empty credentials before reaching the use case

---

#### `interfaces/rest/dto/AuthResponse.java` — Outbound DTO

```java
package com.glowzi.identity.interfaces.rest.dto;

public class AuthResponse {
    private Long userId;
    private String role;
    private String token;

    public AuthResponse(Long userId, String role, String token) { ... }

    // Getters only — immutable
}
```

**Best Practices**:
- ✅ Immutable (getters only) — response data shouldn't be mutated after construction
- ✅ Constructor enforces all fields — no partially-built responses
- ✅ Used by both register and login flows — consistent API shape

---

### 4.5 Configuration & Migrations

#### `application.yml`

```yaml
spring:
  application:
    name: identity-service
  datasource:
    url: jdbc:postgresql://localhost:5433/identity_db
    username: app
    password: app
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

server:
  port: 8081

app:
  jwt:
    secret: 0123456789012345678901234567890123456789012345678901234567890123
    expiration-minutes: 1440    # 24 hours

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**Best Practices**:
- ✅ `ddl-auto: validate` — Hibernate checks schema but never modifies it; Flyway owns DDL
- ✅ JWT config is externalized under `app.jwt.*` — can be overridden by env vars
- ✅ Actuator exposes only `health` and `info` — minimal attack surface
- ⚠️ **Production**: override `app.jwt.secret` with a real 256-bit key via environment variable

---

#### `GlowziIdentityServiceApplication.java` — Entry Point

```java
package com.glowzi.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GlowziIdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GlowziIdentityServiceApplication.class, args);
    }
}
```

**Best Practices**:
- ✅ `@SpringBootApplication` at root package — component scan covers all sub-packages automatically
- ✅ No manual `@ComponentScan` or `@EnableJpaRepositories` needed

---

#### `db/migration/V1__init.sql` — Initial Schema

```sql
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### `db/migration/V2__add_user_profile_columns.sql` — Schema Evolution

```sql
ALTER TABLE users ADD COLUMN full_name           VARCHAR(100);
ALTER TABLE users ADD COLUMN preferred_language   VARCHAR(10);
ALTER TABLE users ADD COLUMN updated_at           TIMESTAMP;
```

**Best Practices**:
- ✅ Flyway versioned naming (`V1__`, `V2__`) — guarantees execution order
- ✅ Additive migrations (`ADD COLUMN`) — no data loss, backward-compatible
- ✅ `BIGSERIAL` for auto-incrementing IDs — matches `GenerationType.IDENTITY`

---

## 5. Best Practices Applied

### Architecture & Design

| Practice | Where Applied |
|----------|---------------|
| **Hexagonal Architecture** | `domain/` (ports) → `infrastructure/` (adapters) |
| **Dependency Inversion** | Use cases depend on interfaces, not implementations |
| **Single Responsibility** | Each class has one reason to change |
| **Domain Purity** | `User.java` and `UserRepository.java` have zero Spring imports |
| **Port/Adapter Pattern** | `UserRepositoryAdapter` bridges domain ↔ JPA |

### Spring & JPA

| Practice | Where Applied |
|----------|---------------|
| **Constructor Injection** | All `@Service` / `@Repository` / `@Component` classes |
| **Separate JPA Entity** | `UserEntity` (JPA) is distinct from `User` (domain) |
| **`@Enumerated(STRING)`** | `UserRole` stored as text, not ordinal |
| **`@Transactional`** | Both `RegisterUserUseCase` and `LoginUserUseCase` |
| **Flyway for DDL** | `ddl-auto: validate` + versioned SQL migrations |
| **Spring Data Query Derivation** | `findByPhone`, `existsByPhone` — zero manual SQL |
| **`@Valid` on Controller** | Triggers Bean Validation before use case execution |
| **`@RestControllerAdvice`** | Centralized, consistent error responses |

### Security

| Practice | Where Applied |
|----------|---------------|
| **BCrypt Password Hashing** | `BCryptPasswordHasher` — salted, slow by design |
| **Password Never Stored Raw** | `passwordHasher.hash()` called before `User.create()` |
| **jjwt Library** | `JwtServiceImpl` — production-grade, signed JWT tokens |
| **Externalized JWT Secret** | `@Value("${app.jwt.secret}")` — from `application.yml` |
| **Configurable Token Expiry** | `@Value("${app.jwt.expiration-minutes}")` — 1440 min default |
| **Generic Auth Errors** | Same message for "user not found" and "wrong password" |
| **Minimal Actuator Exposure** | Only `health` and `info` endpoints enabled |

### Code Quality

| Practice | Where Applied |
|----------|---------------|
| **Immutable Domain Entity** | `User` — getters only, no setters |
| **Factory Method** | `User.create()` — encapsulates creation rules |
| **DTO Validation** | `@NotBlank`, `@NotNull` on request DTOs |
| **`Optional` Return Types** | `findByPhone()` — forces null handling |
| **Thin Controllers** | `AuthController` — zero logic, pure delegation |
| **Consistent Error Shape** | `{"error": "message"}` via `GlobalExceptionHandler` |

---

## 6. Database Schema

After both migrations, the `users` table:

| Column               | Type           | Constraints                          |
|----------------------|----------------|--------------------------------------|
| `id`                 | `BIGSERIAL`    | `PRIMARY KEY`                        |
| `phone`              | `VARCHAR(20)`  | `NOT NULL UNIQUE`                    |
| `password_hash`      | `VARCHAR(255)` | `NOT NULL`                           |
| `role`               | `VARCHAR(30)`  | `NOT NULL`                           |
| `created_at`         | `TIMESTAMP`    | `NOT NULL DEFAULT CURRENT_TIMESTAMP` |
| `full_name`          | `VARCHAR(100)` | nullable                             |
| `preferred_language` | `VARCHAR(10)`  | nullable                             |
| `updated_at`         | `TIMESTAMP`    | nullable                             |

---

## 7. Dependencies (pom.xml)

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST API (Tomcat, Jackson, Spring MVC) |
| `spring-boot-starter-data-jpa` | JPA / Hibernate / Spring Data |
| `spring-boot-starter-security` | Spring Security (BCrypt, filter chain) |
| `spring-boot-starter-validation` | Jakarta Bean Validation (`@NotBlank`, etc.) |
| `spring-boot-starter-actuator` | Health check & monitoring endpoints |
| `flyway-core` + `flyway-database-postgresql` | Versioned database migrations |
| `postgresql` | PostgreSQL JDBC driver (runtime) |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.12.5) | JWT token generation & parsing |
| `lombok` | Boilerplate reduction (optional, compile-time) |
| `spring-boot-devtools` | Hot reload during development (runtime, optional) |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ (test) |
| `spring-boot-testcontainers` + `testcontainers/postgresql` | Integration testing with real PostgreSQL (test) |
| `spring-security-test` | Security-aware test utilities (test) |

---

## 8. How to Run

### Prerequisites

- **Java 21+**
- **PostgreSQL** running on `localhost:5433` with database `identity_db` (user: `app`, password: `app`)
- Or use the provided Docker Compose:

```cmd
cd c:\Users\DELL\Downloads\glowzi-backend\infra
docker-compose up -d
```

### Build & Run

```cmd
cd c:\Users\DELL\Downloads\glowzi-backend\glowzi-identity-service
mvnw.cmd clean compile
mvnw.cmd spring-boot:run
```

### Verify Health

```cmd
curl http://localhost:8081/actuator/health
```

Expected: `{"status":"UP"}`

---

## 9. API Reference

### `POST /auth/register` — Register a new user

**Request**:
```json
{
  "fullName": "Ahmed Ali",
  "phone": "+966501234567",
  "password": "SecureP@ss1",
  "role": "CUSTOMER",
  "preferredLanguage": "ar"
}
```

**Success Response** — `201 Created`:
```json
{
  "userId": 1,
  "role": "CUSTOMER",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Error Response** — `400 Bad Request` (duplicate phone):
```json
{
  "error": "Phone already registered"
}
```

**Error Response** — `400 Bad Request` (validation failure):
```json
{
  "error": "Validation failed"
}
```

---

### `POST /auth/login` — Authenticate an existing user

**Request**:
```json
{
  "phone": "+966501234567",
  "password": "SecureP@ss1"
}
```

**Success Response** — `201 Created`:
```json
{
  "userId": 1,
  "role": "CUSTOMER",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Error Response** — `400 Bad Request` (wrong phone or password):
```json
{
  "error": "Invalid credentials"
}
```

---

### JWT Token Claims

The generated JWT includes:

| Claim | Type | Example | Description |
|-------|------|---------|-------------|
| `sub` | String | `"1"` | User ID |
| `phone` | String | `"+966501234567"` | User phone number |
| `role` | String | `"CUSTOMER"` | User role |
| `iat` | Number | `1711267200` | Issued-at timestamp (epoch seconds) |
| `exp` | Number | `1711353600` | Expiration timestamp (epoch seconds) |

---

## 10. Future Improvements

| Priority | Task | Details |
|----------|------|---------|
| 🔴 High | **Spring Security filter chain** | Permit `/auth/**` without authentication, secure all other endpoints with JWT filter |
| 🔴 High | **Externalize secret via env var** | `app.jwt.secret: ${JWT_SECRET}` — never commit real secrets |
| 🟡 Medium | **JWT validation endpoint / filter** | Parse & validate incoming JWTs for protected endpoints |
| 🟡 Medium | **Refresh token flow** | Short-lived access token + long-lived refresh token |
| 🟡 Medium | **Phone validation regex** | Add `@Pattern(regexp = "^\\+[1-9]\\d{6,14}$")` on `phone` fields |
| 🟡 Medium | **Detailed validation errors** | Return per-field error messages from `MethodArgumentNotValidException` |
| 🟢 Low | **Rename `exectue()` → `execute()`** | Fix the typo in `RegisterUserUseCase` |
| 🟢 Low | **Use Java records for DTOs** | `record RegisterUserRequest(...)` — eliminates getter/setter boilerplate |
| 🟢 Low | **Integration tests** | Use Testcontainers (already in pom.xml) for full DB round-trip tests |
| 🟢 Low | **`@ReadOnlyTransaction` for login** | Login is a read-only operation — `@Transactional(readOnly = true)` |

---

## Dependency Flow Diagram

```
                       ┌──────────────────────┐
                       │   AuthController      │  ← @RestController
                       │  POST /auth/register  │
                       │  POST /auth/login     │
                       └──────┬───────┬────────┘
                              │       │
               ┌──────────────┘       └──────────────┐
               ▼                                     ▼
┌──────────────────────────┐          ┌──────────────────────────┐
│  RegisterUserUseCase     │          │  LoginUserUseCase        │
│  @Service                │          │  @Service                │
└──────┬──────┬──────┬─────┘          └──────┬──────┬──────┬─────┘
       │      │      │                       │      │      │
       ▼      ▼      ▼                       ▼      ▼      ▼
  UserRepo  PwdHash  JwtSvc  ◄── Port Interfaces (domain/ & application/)
       │      │      │
       ▼      ▼      ▼
┌──────────┐ ┌────────────┐ ┌──────────────┐
│UserRepo  │ │BCryptPwd   │ │JwtServiceImpl│  ◄── @Repository / @Component
│Adapter   │ │Hasher      │ │(jjwt)        │      (infrastructure/)
└────┬─────┘ └────────────┘ └──────────────┘
     │
     ▼
┌──────────────────┐
│JpaUserRepository │  ◄── Spring Data (auto-generated proxy)
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│   UserEntity     │  ◄── @Entity (JPA)
└────┬─────────────┘
     │
     ▼
┌──────────────────┐
│  PostgreSQL      │  ◄── "users" table (Flyway-managed)
│  identity_db     │
└──────────────────┘
```

---

*Generated for `glowzi-identity-service` — Glowzi Backend · Last updated 24 March 2026*
