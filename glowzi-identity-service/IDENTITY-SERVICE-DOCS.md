# Glowzi Identity Service — Full Documentation

> **Module**: `glowzi-identity-service`  
> **Stack**: Java 21 · Spring Boot 3.5 · Spring Data JPA · Spring Security · PostgreSQL · Flyway · Keycloak  
> **Architecture**: Hexagonal (Ports & Adapters) / Clean Architecture + DDD  
> **Last updated**: 27 March 2026

---

## Table of Contents

1. [What is Hexagonal Architecture? (Simple Explanation)](#1-what-is-hexagonal-architecture-simple-explanation)
2. [What is DDD? (Simple Explanation)](#2-what-is-ddd-simple-explanation)
3. [How a Request Flows Through the System](#3-how-a-request-flows-through-the-system)
4. [Package Structure](#4-package-structure)
5. [Domain Layer — Every File Explained](#5-domain-layer--every-file-explained)
6. [Application Layer — Every File Explained](#6-application-layer--every-file-explained)
7. [Infrastructure Layer — Every File Explained](#7-infrastructure-layer--every-file-explained)
8. [Interfaces Layer (REST) — Every File Explained](#8-interfaces-layer-rest--every-file-explained)
9. [Database Schema & Migrations](#9-database-schema--migrations)
10. [Configuration Files](#10-configuration-files)
11. [Test Suite Explained](#11-test-suite-explained)
12. [Problems Solved (Changelog)](#12-problems-solved-changelog)
13. [API Reference](#13-api-reference)
14. [What is Still Missing (Roadmap)](#14-what-is-still-missing-roadmap)

---

## 1. What is Hexagonal Architecture? (Simple Explanation)

Imagine your business logic (the rules of your app) is a **box in the center**.

Everything else — the database, the web API, Keycloak, SMS senders — are **plugins** that connect to the outside of that box. The box does NOT know or care about those plugins. It only knows about contracts (Java interfaces called **ports**).

```
                  ┌────────────────────────────────────┐
  HTTP Request ──▶│  interfaces/rest (AuthController)  │
                  │         ↓                          │
                  │  application/ (Use Cases)          │  ← The CENTER box
                  │     RegisterUserUseCase            │     Pure Java logic
                  │     LoginUserUseCase               │     No Spring here
                  │         ↓                          │
                  │  domain/ (User, VOs, Rules)        │
                  └────────────────────────────────────┘
                         ↓              ↓
              infrastructure/      infrastructure/
              persistence/         security/
              (PostgreSQL)         (Keycloak)
```

**The golden rule**: arrows point INWARD. The domain never imports anything from infrastructure. If you want to change from PostgreSQL to MongoDB, only the infrastructure layer changes — zero domain code changes.

**Why this matters for you as a developer:**
- You can test domain logic without starting a server or a database
- You can swap Keycloak for Auth0 by replacing one class
- Each layer has one job and is easy to understand alone

---

## 2. What is DDD? (Simple Explanation)

**Domain-Driven Design (DDD)** is a way of structuring code so it mirrors the real business problem.

Key DDD concepts used in this service:

| Concept | Simple Meaning | Example in this service |
|---------|---------------|------------------------|
| **Aggregate Root** | The main object that controls a group of related objects. You always go through it to make changes. | `User` — you call `User.register(...)`, not `new User()` directly |
| **Value Object (VO)** | An immutable object defined by its VALUE, not by an ID. Two VOs with the same value are equal. | `Phone("+966501234567")`, `FullName("Ahmed")`, `HashedPassword(...)` |
| **Domain Event** | A record of something that happened. "A user registered." Published AFTER save so listeners can react. | `UserRegisteredEvent` |
| **Repository Port** | The domain's shopping list: "I need to save and find Users." The domain doesn't care HOW it's done. | `UserRepository` interface in domain/ |
| **Use Case** | One specific thing the system can do. One class = one action. | `RegisterUserUseCase`, `LoginUserUseCase` |
| **Factory Method** | A static method that creates objects and enforces business rules at creation time. | `User.register(...)` |

---

## 3. How a Request Flows Through the System

### Registration Flow: `POST /auth/register`

```
1. HTTP POST /auth/register
   Body: { fullName, phone, password, role, preferredLanguage }
   │
   ▼
2. AuthController.register()
   - Spring validates @Valid annotations (blank checks, not-null checks)
   - Maps RegisterUserRequest DTO → RegisterUserCommand (plain Java object)
   - Calls registerUserUseCase.execute(command)
   │
   ▼
3. RegisterUserUseCase.execute()
   - Creates Phone VO → validates E.164 format (throws if invalid)
   - Creates FullName VO → validates not-blank, max 100 chars
   - Checks userRepository.existsByPhone(phone) → throws if duplicate
   - Calls identityProvider.createUser() → creates user in Keycloak
   - Creates User.register(...) → domain aggregate, emits UserRegisteredEvent
   - Calls userRepository.save(user) → persists to PostgreSQL
   - Publishes UserRegisteredEvent via DomainEventPublisher
   - Calls identityProvider.authenticate() → gets JWT token from Keycloak
   - Returns AuthResult(userId, role, token)
   │
   ▼
4. AuthController maps AuthResult → AuthResponse DTO
   Returns: HTTP 201 { userId, role, token }
```

### Login Flow: `POST /auth/login`

```
1. HTTP POST /auth/login
   Body: { phone, password }
   │
   ▼
2. AuthController.login()
   - Validates @Valid
   - Maps LoginRequest → LoginCommand
   - Calls loginUserUseCase.execute(command)
   │
   ▼
3. LoginUserUseCase.execute()
   - Creates Phone VO → validates format
   - Calls identityProvider.authenticate(phone, password)
     → Keycloak verifies credentials, returns JWT token
     → If wrong credentials, throws IdentityProviderAuthException → caught → InvalidCredentialsException
   - Calls userRepository.findByPhone(phone) to get local user data (role, id)
     → If user not found, throws InvalidCredentialsException
   - Returns AuthResult(userId, role, token)
   │
   ▼
4. Returns: HTTP 200 { userId, role, token }
```

---

## 4. Package Structure

```
com.glowzi.identity/
│
├── GlowziIdentityServiceApplication.java    # Spring Boot entry point (@SpringBootApplication)
│
├── domain/                                  # ← LAYER 1: Pure Java. Zero Spring. Zero JPA.
│   ├── User.java                            # Aggregate root — the heart of this service
│   ├── UserRepository.java                  # Port interface (contract for persistence)
│   ├── enums/
│   │   └── UserRole.java                    # CUSTOMER, PROVIDER, ADMIN
│   ├── event/
│   │   ├── DomainEvent.java                 # Marker interface for all events
│   │   └── UserRegisteredEvent.java         # Event: "a user registered"
│   ├── exception/
│   │   ├── DomainException.java             # Base class for all domain exceptions
│   │   ├── InvalidCredentialsException.java # Thrown on bad login
│   │   └── PhoneAlreadyRegisteredException.java
│   └── vo/
│       ├── Phone.java                       # Self-validating phone number (E.164)
│       ├── FullName.java                    # Self-validating name (non-blank, ≤100 chars)
│       └── HashedPassword.java              # Self-validating hash (non-blank, masked)
│
├── application/                             # ← LAYER 2: Use cases & port interfaces
│   ├── RegisterUserUseCase.java             # Orchestrates registration
│   ├── LoginUserUseCase.java                # Orchestrates login
│   ├── PasswordHasher.java                  # Port: hash & verify passwords
│   ├── JwtService.java                      # Port: get tokens
│   ├── IdentityProviderService.java         # Port: create users in Keycloak, authenticate
│   ├── DomainEventPublisher.java            # Port: publish domain events
│   ├── command/
│   │   ├── RegisterUserCommand.java         # Input to RegisterUserUseCase
│   │   └── LoginCommand.java                # Input to LoginUserUseCase
│   └── result/
│       └── AuthResult.java                  # Output of both use cases
│
├── infrastructure/                          # ← LAYER 3: Spring beans, JPA, Keycloak, events
│   ├── persistence/
│   │   ├── UserEntity.java                  # JPA @Entity — mapped to "users" table
│   │   ├── JpaUserRepository.java           # Spring Data interface (auto-implemented)
│   │   └── UserRepositoryAdapter.java       # @Repository — implements UserRepository port
│   ├── security/
│   │   ├── BCryptPasswordHasher.java        # @Component — implements PasswordHasher port
│   │   ├── JwtServiceImpl.java              # @Component — delegates to Keycloak
│   │   ├── KeycloakUserService.java         # @Component — implements IdentityProviderService
│   │   └── SecurityConfig.java             # Spring Security config (JWT validation)
│   └── event/
│       └── SpringDomainEventPublisher.java  # @Component — implements DomainEventPublisher
│
└── interfaces/                              # ← LAYER 4: HTTP boundary
    └── rest/
        ├── AuthController.java              # @RestController — /auth/register, /auth/login
        ├── GlobalExceptionHandler.java      # @RestControllerAdvice — maps exceptions to HTTP
        └── dto/
            ├── RegisterUserRequest.java     # Inbound DTO with @Valid annotations
            ├── LoginRequest.java            # Inbound DTO with @Valid annotations
            └── AuthResponse.java            # Outbound DTO { userId, role, token }
```

---

## 5. Domain Layer — Every File Explained

> **Rule**: No Spring annotations. No JPA. No HTTP. Pure Java business logic.
> You can run every class here in a plain `main()` method with no server.

---

### `domain/User.java` — The Aggregate Root

**What is it?** The central object of this entire service. Every action — register, login, change password — goes through `User`.

**Why no setters?** Because a user shouldn't randomly change their phone number from outside. All changes go through behavior methods. This is called **encapsulation**.

```java
public class User {

    // All fields use Value Objects, not raw Strings
    // This means you can NEVER have a User with an invalid phone
    private Long id;
    private FullName fullName;    // not String — FullName validates itself
    private Phone phone;          // not String — Phone validates E.164 format
    private HashedPassword passwordHash; // not String — masks in toString()
    private UserRole role;
    private String preferredLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Domain events collected during this aggregate's life
    // They are published AFTER the aggregate is saved to DB
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // ── Constructor: used ONLY when loading from DB ─────────────────
    // Why? When loading from DB, data is already validated.
    // We just reconstruct the object.
    public User(Long id, FullName fullName, Phone phone, ...) { ... }

    // ── Factory method: the ONLY way to create a NEW user ───────────
    // Why a factory method and not just new User()?
    // Because creation has BUSINESS RULES:
    //   - role must not be null
    //   - timestamps must be set to "now"
    //   - a UserRegisteredEvent must be emitted
    // Putting this in a constructor would be messy and hard to read.
    public static User register(FullName fullName, Phone phone,
                                HashedPassword passwordHash,
                                UserRole role, String preferredLanguage) {
        if (role == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User(null, fullName, phone, passwordHash,
                             role, preferredLanguage, now, now);

        // id = null here because the DB has not yet assigned one
        // The event also has userId=null — it gets enriched after save
        user.domainEvents.add(new UserRegisteredEvent(null, phone.value(), role, now));
        return user;
    }

    // ── Behavior method (not a setter!) ─────────────────────────────
    // "Does this raw password match my stored hash?"
    // This is a DOMAIN QUESTION — it belongs on the User, not in the use case.
    // The PasswordMatchChecker is passed in as a lambda so the domain
    // doesn't depend on BCrypt (which lives in infrastructure).
    public boolean passwordMatches(String rawPassword, PasswordMatchChecker checker) {
        return checker.matches(rawPassword, this.passwordHash.value());
    }

    // ── Domain Event helpers ─────────────────────────────────────────
    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents); // caller cannot modify the list
    }
    public void clearDomainEvents() { domainEvents.clear(); }

    // ── Getters only — no setters ────────────────────────────────────
    public Long getId() { return id; }
    // ...etc
}
```

---

### `domain/vo/Phone.java` — Value Object

**What is it?** A wrapper around a phone number string that validates the format automatically. You can NEVER have a `Phone` object with an invalid number.

**What is E.164 format?** International phone format: `+` followed by country code + number. Example: `+966501234567`. No spaces, no dashes.

```java
// "record" in Java = immutable class with value-based equality
// Two Phone("+966...") objects are EQUAL because they have the same value
// Compare to regular classes where two objects with same data are NOT equal by default
public record Phone(String value) {

    private static final String E164_PATTERN = "^\\+[1-9]\\d{6,14}$";
    // ^        = start of string
    // \\+      = literal + sign
    // [1-9]    = first digit after + can't be 0
    // \\d{6,14}= 6 to 14 more digits
    // $        = end of string

    // This is a "compact constructor" in Java records
    // It runs before the record is created
    public Phone {
        if (value == null || !value.matches(E164_PATTERN)) {
            throw new IllegalArgumentException(
                "Phone must be in E.164 format (e.g. +966501234567), got: " + value);
        }
    }
}

// Usage:
// Phone p = new Phone("+966501234567");  ✅ works
// Phone p = new Phone("0501234567");     ❌ throws IllegalArgumentException immediately
// Phone p = new Phone(null);             ❌ throws immediately
```

---

### `domain/vo/FullName.java` — Value Object

```java
public record FullName(String value) {

    public FullName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        if (value.length() > 100) {
            throw new IllegalArgumentException("Full name must not exceed 100 characters");
        }
    }
}
// value.isBlank() = true for "" or "   " (whitespace only)
```

---

### `domain/vo/HashedPassword.java` — Value Object

```java
public record HashedPassword(String value) {

    public HashedPassword {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Hashed password must not be blank");
        }
    }

    // IMPORTANT: toString() masks the hash so it never appears in logs
    // Without this, your logs might show: User{passwordHash=$2a$10$abc...}
    @Override
    public String toString() {
        return "HashedPassword[***]";
    }
}
```

---

### `domain/UserRepository.java` — Port Interface

**What is it?** The domain's declaration of what it NEEDS from persistence. It does not care about SQL or JPA.

```java
public interface UserRepository {

    // Save a User — if id is null, INSERT; if id is set, UPDATE
    User save(User user);

    // Find by phone number (our unique identifier)
    Optional<User> findByPhone(Phone phone);

    // Quick check — does a user with this phone exist? (Used to prevent duplicates)
    boolean existsByPhone(Phone phone);
}

// Why Optional<User> and not User?
// Optional forces callers to handle "not found" explicitly.
// Without Optional: user.getRole() would crash with NullPointerException if user wasn't found.
// With Optional: you MUST call .orElseThrow() or .orElse() — the compiler reminds you.
```

---

### `domain/event/DomainEvent.java` — Marker Interface

```java
// A marker interface — has no methods.
// Its only purpose is to say "this class IS a domain event"
// So DomainEventPublisher can have a type-safe signature: publish(DomainEvent)
public interface DomainEvent {
}
```

---

### `domain/event/UserRegisteredEvent.java` — Domain Event

**What is a domain event?** A record of something that happened. Like a fact written in the past tense: "A user WAS registered."

**Why publish events?** Because other parts of the system might want to react. Examples:
- Send a welcome SMS to the phone number
- Update an analytics counter
- Create a default profile in another service

All of this happens WITHOUT modifying `RegisterUserUseCase` — you just add a new `@EventListener`.

```java
// "record" = immutable. Events never change after they happen.
public record UserRegisteredEvent(
        Long userId,         // the DB-assigned ID (null before save, set after)
        String phone,        // phone number as String (not VO — events should be simple)
        UserRole role,       // CUSTOMER / PROVIDER / ADMIN
        LocalDateTime registeredAt  // when it happened
) implements DomainEvent {
}

// How to listen to this event (add this anywhere in your Spring app):
// @EventListener
// public void onUserRegistered(UserRegisteredEvent event) {
//     smsService.sendWelcome(event.phone());
// }
```

---

### `domain/exception/DomainException.java` — Base Exception

```java
// abstract = cannot instantiate DomainException directly
// Forces you to use specific subclasses like PhoneAlreadyRegisteredException
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}

// Why RuntimeException and not Exception?
// Checked exceptions (Exception) require try/catch everywhere — very verbose.
// RuntimeExceptions propagate up automatically and are caught by GlobalExceptionHandler.
```

---

### `domain/exception/PhoneAlreadyRegisteredException.java`

```java
public class PhoneAlreadyRegisteredException extends DomainException {
    public PhoneAlreadyRegisteredException(String phone) {
        super("Phone already registered: " + phone);
    }
}
// Caught by GlobalExceptionHandler → returns HTTP 409 Conflict
```

---

### `domain/exception/InvalidCredentialsException.java`

```java
public class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
        super("Invalid credentials");  // deliberately vague message
    }
}
// WHY vague? Security reason: if we said "phone not found" vs "wrong password",
// an attacker could figure out which phone numbers are registered.
// "Invalid credentials" tells them nothing useful.
// Caught by GlobalExceptionHandler → returns HTTP 401 Unauthorized
```

---

## 6. Application Layer — Every File Explained

> **Rule**: Orchestrates domain logic. Uses ports (interfaces) only — never concrete implementations.
> The use cases are the "scripts" that describe what the system does, step by step.

---

### `application/RegisterUserUseCase.java`

**What does it do?** Coordinates everything needed to register a new user.

```java
@Service  // Spring manages this as a singleton bean
public class RegisterUserUseCase {

    // All three are INTERFACES (ports), not concrete classes
    // Spring injects the real implementations at startup
    private final UserRepository userRepository;             // → UserRepositoryAdapter
    private final IdentityProviderService identityProvider;  // → KeycloakUserService
    private final DomainEventPublisher eventPublisher;       // → SpringDomainEventPublisher

    // Constructor injection (preferred over @Autowired on fields)
    // Why constructor injection?
    // - Makes dependencies explicit and visible
    // - Easy to test: just pass mock objects in the constructor
    // - Cannot forget to set a dependency (would be null with field injection)
    public RegisterUserUseCase(UserRepository userRepository,
                               IdentityProviderService identityProvider,
                               DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional  // If anything fails, the DB save is rolled back automatically
    public AuthResult execute(RegisterUserCommand command) {

        // Step 1: Create VOs — these validate themselves
        // If phone is "0501234567" (missing +), this line throws immediately
        Phone phone = new Phone(command.phone());
        FullName fullName = new FullName(command.fullName());

        // Step 2: Check duplicate — business rule enforced here, not in the controller
        if (userRepository.existsByPhone(phone)) {
            throw new PhoneAlreadyRegisteredException(phone.value());
        }

        // Step 3: Create user in Keycloak (external identity provider)
        // Keycloak stores the real password. Our DB stores "KEYCLOAK_MANAGED" as a placeholder.
        UserRole role = UserRole.valueOf(command.role()); // "CUSTOMER" → UserRole.CUSTOMER
        try {
            identityProvider.createUser(
                phone.value(),    // username in Keycloak = phone number
                fullName.value(), // display name
                phone.value(),    // phone attribute
                command.rawPassword(),
                role.name()       // Keycloak realm role name
            );
        } catch (IdentityProviderService.IdentityProviderConflictException e) {
            // Keycloak said "user already exists" — re-throw as our domain exception
            throw new PhoneAlreadyRegisteredException(phone.value());
        }

        // Step 4: Save app-specific data in our local PostgreSQL
        // Password is "KEYCLOAK_MANAGED" — we don't store the real password locally
        HashedPassword placeholder = new HashedPassword("KEYCLOAK_MANAGED");
        User user = User.register(fullName, phone, placeholder, role, command.preferredLanguage());
        User saved = userRepository.save(user);  // saved.getId() is now the DB-assigned ID

        // Step 5: Publish domain events that were collected on the aggregate
        // IMPORTANT: publish AFTER save so events carry the real DB id
        eventPublisher.publishAll(saved.getDomainEvents());
        saved.clearDomainEvents();  // prevent double-publishing

        // Step 6: Get the JWT token from Keycloak
        String token = identityProvider.authenticate(phone.value(), command.rawPassword());

        // Step 7: Return a simple record with what the controller needs
        return new AuthResult(saved.getId(), saved.getRole().name(), token);
    }
}
```

---

### `application/LoginUserUseCase.java`

```java
@Service
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final IdentityProviderService identityProvider;

    @Transactional(readOnly = true)  // readOnly = hint to DB: no writes, optimize for reads
    public AuthResult execute(LoginCommand command) {

        Phone phone = new Phone(command.phone()); // validates format

        // Step 1: Try to authenticate against Keycloak
        // Keycloak checks the real password. We never store or check it ourselves.
        String token;
        try {
            token = identityProvider.authenticate(phone.value(), command.rawPassword());
        } catch (IdentityProviderService.IdentityProviderAuthException e) {
            // Keycloak said "wrong credentials" → we say "Invalid credentials"
            // (same message — doesn't reveal if the phone exists or not)
            throw new InvalidCredentialsException();
        }

        // Step 2: Load local user data (userId, role) from our DB
        User user = userRepository.findByPhone(phone)
                .orElseThrow(InvalidCredentialsException::new);
        // If user authenticated with Keycloak but doesn't exist in our DB,
        // something is wrong — treat as invalid credentials

        return new AuthResult(user.getId(), user.getRole().name(), token);
    }
}
```

---

### `application/IdentityProviderService.java` — Port Interface

**What is it?** A contract saying "I need something that can create users and authenticate them." The application layer doesn't know it's Keycloak — it could be Auth0, AWS Cognito, or anything else.

```java
public interface IdentityProviderService {

    // Creates a user in the external provider
    // Returns the provider's internal user ID (e.g. Keycloak UUID)
    String createUser(String username, String fullName, String phone,
                      String rawPassword, String roleName);

    // Authenticates and returns a JWT access token string
    String authenticate(String username, String password);

    // Exception hierarchy — defined HERE (application layer) not in infrastructure
    // Why? So use cases can catch them without knowing about Keycloak
    class IdentityProviderException extends RuntimeException { ... }
    class IdentityProviderConflictException extends IdentityProviderException { ... }
    class IdentityProviderAuthException extends IdentityProviderException { ... }
}
```

---

### `application/DomainEventPublisher.java` — Port Interface

```java
public interface DomainEventPublisher {

    // Publish one event
    void publish(DomainEvent event);

    // Publish all events from an aggregate (default method = free implementation)
    // "default" in interfaces means: subclasses get this for free unless they override it
    default void publishAll(List<DomainEvent> events) {
        events.forEach(this::publish);
        // this::publish = method reference = same as: event -> this.publish(event)
    }
}
```

---

### `application/command/RegisterUserCommand.java`

```java
// "record" = immutable data carrier. All fields set at construction, no setters.
// Commands represent the INTENT to do something.
// They carry data from the controller into the use case.
public record RegisterUserCommand(
        String fullName,
        String phone,
        String rawPassword,
        String role,           // String here — converted to UserRole enum in the use case
        String preferredLanguage  // nullable — optional field
) {}
```

---

### `application/result/AuthResult.java`

```java
// Carries the result OUT of the use case back to the controller
public record AuthResult(
        Long userId,    // the DB-assigned ID
        String role,    // "CUSTOMER", "PROVIDER", or "ADMIN"
        String token    // Keycloak JWT access token
) {}
```

---

## 7. Infrastructure Layer — Every File Explained

> **Rule**: All Spring annotations live here. All JPA annotations live here. All Keycloak code lives here.
> This layer implements every port defined in domain/ and application/.

---

### `infrastructure/persistence/UserEntity.java` — JPA Entity

**Why is this separate from `domain/User.java`?**

Because `domain/User` is a business object. `UserEntity` is a database mapping object. They have different jobs:
- `User` has behavior methods, Value Objects, domain events
- `UserEntity` has JPA annotations, setters (JPA needs them), column mappings

```java
@Entity               // tells JPA "this class maps to a DB table"
@Table(name = "users") // the exact table name in PostgreSQL
public class UserEntity {

    @Id                              // this is the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // DB auto-generates with BIGSERIAL
    private Long id;

    @Column(name = "full_name", nullable = false)  // snake_case column name
    private String fullName;

    @Column(nullable = false, unique = true)  // phone is unique — prevents duplicates at DB level
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)   // stores "CUSTOMER" not 0 in the DB
    // WHY? If you use EnumType.ORDINAL (the default), the DB stores 0,1,2...
    // If you ever reorder the enum, all data breaks. STRING is always safe.
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "preferred_language")  // nullable — optional field
    private String preferredLanguage;

    // Getters AND setters — JPA needs setters to populate the entity after a DB read
    // (unlike domain/User which has no setters)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    // ... etc
}
```

---

### `infrastructure/persistence/JpaUserRepository.java` — Spring Data

```java
// This interface has NO implementation code
// Spring Data sees it at startup and generates the SQL automatically
public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

    // Spring Data reads the method name and generates:
    // SELECT * FROM users WHERE phone = ?
    Optional<UserEntity> findByPhone(String phone);

    // Generates: SELECT COUNT(*) > 0 FROM users WHERE phone = ?
    boolean existsByPhone(String phone);
}

// JpaRepository<UserEntity, Long> gives you for free:
// save(), findById(), findAll(), delete(), count(), etc.
```

---

### `infrastructure/persistence/UserRepositoryAdapter.java` — The Bridge

**What is it?** The adapter that connects the domain port (`UserRepository`) to the JPA layer (`JpaUserRepository`). This is the most important infrastructure class because it's the Anti-Corruption Layer.

```java
@Repository  // Spring bean — will be injected wherever UserRepository is needed
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);   // domain → JPA (unwrap VOs to Strings)
        UserEntity saved = jpaUserRepository.save(entity);  // JPA does the SQL
        return toDomain(saved);               // JPA → domain (wrap Strings in VOs)
    }

    @Override
    public Optional<User> findByPhone(Phone phone) {
        // phone.value() unwraps the VO to get the String "+966501234567"
        return jpaUserRepository.findByPhone(phone.value())
                .map(this::toDomain);  // convert found UserEntity to domain User
    }

    @Override
    public boolean existsByPhone(Phone phone) {
        return jpaUserRepository.existsByPhone(phone.value());
    }

    // ── Domain → JPA Entity ──────────────────────────────────────────
    private UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.setId(user.getId());                     // null for new users
        entity.setFullName(user.getFullName().value()); // unwrap VO: FullName → String
        entity.setPhone(user.getPhone().value());       // unwrap VO: Phone → String
        entity.setPasswordHash(user.getPasswordHash().value()); // unwrap VO
        entity.setRole(user.getRole());
        entity.setPreferredLanguage(user.getPreferredLanguage());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    // ── JPA Entity → Domain ──────────────────────────────────────────
    private User toDomain(UserEntity entity) {
        return new User(
            entity.getId(),
            new FullName(entity.getFullName()),           // wrap String in VO
            new Phone(entity.getPhone()),                 // wrap String in VO
            new HashedPassword(entity.getPasswordHash()), // wrap String in VO
            entity.getRole(),
            entity.getPreferredLanguage(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

---

### `infrastructure/security/BCryptPasswordHasher.java`

```java
@Component  // Spring singleton bean
public class BCryptPasswordHasher implements PasswordHasher {

    // BCryptPasswordEncoder is Spring Security's BCrypt implementation
    // BCrypt is a slow hashing algorithm — intentionally slow to make brute force hard
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public HashedPassword hash(String rawPassword) {
        // BCrypt adds a random "salt" automatically
        // Same password hashed twice gives DIFFERENT results — prevents rainbow table attacks
        String hashed = encoder.encode(rawPassword);
        return new HashedPassword(hashed);  // wrap in VO
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        // BCrypt's matches() extracts the salt from the hash and rehashes the raw password
        // Returns true if they match
        return encoder.matches(rawPassword, hashedPassword);
    }
}
```

---

### `infrastructure/security/KeycloakUserService.java` — Keycloak Adapter

**What is Keycloak?** An open-source identity server. Instead of building login/password storage/JWT ourselves, we delegate it to Keycloak. Keycloak handles:
- Storing user credentials securely
- Issuing JWT tokens
- Managing roles
- Token expiry and refresh

```java
@Component
public class KeycloakUserService implements IdentityProviderService {

    // All these come from application.yml keycloak: section
    private final String serverUrl;     // http://localhost:9090
    private final String realm;         // glowzi
    private final String clientId;      // glowzi-app
    private final String clientSecret;  // glowzi-app-secret
    private final String adminUsername; // admin
    private final String adminPassword; // admin

    // @Value("${keycloak.base-url}") reads from application.yml
    public KeycloakUserService(
            @Value("${keycloak.base-url}") String serverUrl,
            @Value("${keycloak.realm}") String realm,
            // ... etc
    ) { ... }

    @Override
    public String createUser(String username, String fullName, String phone,
                             String rawPassword, String roleName) {
        // Opens an admin Keycloak client (authenticated as admin)
        try (Keycloak adminClient = buildAdminClient()) {
            RealmResource realmResource = adminClient.realm(realm);

            // Build a UserRepresentation (Keycloak's user object)
            UserRepresentation user = new UserRepresentation();
            user.setUsername(username);   // phone number as username
            user.setFirstName(fullName);
            user.setEnabled(true);        // account is active

            // Set the password
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(rawPassword);
            credential.setTemporary(false); // not temporary — user doesn't need to change it
            user.setCredentials(List.of(credential));

            // Make the API call to Keycloak
            try (Response response = realmResource.users().create(user)) {
                if (response.getStatus() == 409) {
                    throw new KeycloakConflictException("User already exists: " + username);
                }
                if (response.getStatus() != 201) {
                    throw new KeycloakException("Failed. Status: " + response.getStatus());
                }
            }

            // Get the Keycloak-assigned UUID
            String keycloakUserId = realmResource.users()
                .searchByUsername(username, true).get(0).getId();

            // Assign the realm role (CUSTOMER / PROVIDER / ADMIN)
            RoleRepresentation role = realmResource.roles().get(roleName).toRepresentation();
            realmResource.users().get(keycloakUserId).roles().realmLevel()
                .add(List.of(role));

            return keycloakUserId;
        }
    }

    @Override
    public String authenticate(String username, String password) {
        return getToken(username, password).getToken(); // returns just the token string
    }

    // Uses "Resource Owner Password Credentials" grant
    // This means: send username+password directly to Keycloak, get back a JWT
    public AccessTokenResponse getToken(String username, String password) {
        try (Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .username(username)
                .password(password)
                .grantType("password")  // OAuth2 grant type
                .build()) {
            return keycloak.tokenManager().getAccessToken();
        } catch (Exception e) {
            throw new KeycloakAuthException("Authentication failed", e);
        }
    }

    // Admin client uses the Keycloak "master" realm with admin credentials
    // (different from the "glowzi" realm where our users live)
    private Keycloak buildAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .grantType("password")
                .build();
    }
}
```

---

### `infrastructure/security/JwtServiceImpl.java`

```java
@Component
public class JwtServiceImpl implements JwtService {

    private final KeycloakUserService keycloakUserService;

    // This method is intentionally NOT implemented (UnsupportedOperationException)
    // because token generation is fully handled by Keycloak.
    // The JwtService port's generateToken() method is a legacy from before Keycloak integration.
    // TODO: Remove generateToken() from JwtService port in a future cleanup.
    @Override
    public String generateToken(User user) {
        throw new UnsupportedOperationException(
            "Token generation is handled by Keycloak. Use identityProvider.authenticate() instead.");
    }

    // This is the method actually used by the system
    // username = phone number, password = raw password
    public String getToken(String username, String password) {
        AccessTokenResponse tokenResponse = keycloakUserService.getToken(username, password);
        return tokenResponse.getToken();
    }
}
```

---

### `infrastructure/security/SecurityConfig.java`

**What does it do?** Configures Spring Security to:
1. Allow `/auth/**` requests without a token (register/login don't need auth)
2. Require a valid JWT for all other requests
3. Read roles from the JWT's `"roles"` claim and give them `ROLE_` prefix

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // WHY disable CSRF? This is a stateless REST API.
            // CSRF attacks target browser-based session cookies.
            // We use JWT tokens in the Authorization header — not cookies.
            // So CSRF protection is not needed and would break API clients.

            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // STATELESS = never create a server-side session (HttpSession).
            // Every request must carry its own JWT. This is required for microservices.

            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()    // register & login are public
                .requestMatchers("/actuator/**").permitAll() // health checks are public
                .anyRequest().authenticated()               // everything else needs a token
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
            // oauth2ResourceServer = Spring knows to look for "Authorization: Bearer <token>"
            // It validates the token against Keycloak's public keys (from issuer-uri in application.yml)

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    // Keycloak puts roles in the token like: { "roles": ["CUSTOMER"] }
    // Spring Security expects: { "authorities": ["ROLE_CUSTOMER"] }
    // This converter bridges the gap.
    static class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return Collections.emptyList();
            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    // "CUSTOMER" → "ROLE_CUSTOMER"
                    // Spring Security's @PreAuthorize("hasRole('CUSTOMER')") checks for "ROLE_CUSTOMER"
                    .collect(Collectors.toList());
        }
    }
}
```

---

### `infrastructure/event/SpringDomainEventPublisher.java`

```java
@Component
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher springPublisher;
    // ApplicationEventPublisher is a Spring built-in — every Spring context has one

    public SpringDomainEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publish(DomainEvent event) {
        springPublisher.publishEvent(event);
        // This fires the event to ALL @EventListener methods in the application
        // that accept this event type
    }
}

// Example of adding a listener — paste this in any @Service or @Component:
//
// @EventListener
// public void onUserRegistered(UserRegisteredEvent event) {
//     log.info("New user registered: phone={}, role={}", event.phone(), event.role());
//     // Add SMS notification, analytics, etc. here
// }
```

---

## 8. Interfaces Layer (REST) — Every File Explained

> **Rule**: The only place that knows about HTTP. Maps HTTP ↔ application layer.
> No business logic here. No domain exceptions here (those are caught by GlobalExceptionHandler).

---

### `interfaces/rest/AuthController.java`

```java
@RestController         // combines @Controller + @ResponseBody (all methods return JSON)
@RequestMapping("/auth") // all endpoints start with /auth
public class AuthController {

    // Only use cases injected — not repositories, not JPA, not Keycloak
    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)  // returns HTTP 201 on success
    public AuthResponse register(@Valid @RequestBody RegisterUserRequest request) {
        // @Valid tells Spring to run Bean Validation on RegisterUserRequest
        // If validation fails → MethodArgumentNotValidException → GlobalExceptionHandler → 400

        // @RequestBody tells Spring to parse the JSON body into RegisterUserRequest

        // 1. Map DTO → Command (DTO is HTTP-facing, Command is use-case-facing)
        RegisterUserCommand command = new RegisterUserCommand(
                request.getFullName(),
                request.getPhone(),
                request.getPassword(),
                request.getRole().name(),  // UserRole enum → "CUSTOMER" String
                request.getPreferredLanguage()
        );

        // 2. Execute use case (all business logic is here)
        AuthResult result = registerUserUseCase.execute(command);

        // 3. Map Result → Response DTO
        return new AuthResponse(result.userId(), result.role(), result.token());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)  // returns HTTP 200
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.getPhone(), request.getPassword());
        AuthResult result = loginUserUseCase.execute(command);
        return new AuthResponse(result.userId(), result.role(), result.token());
    }
}
```

---

### `interfaces/rest/dto/RegisterUserRequest.java` — Inbound DTO

```java
public class RegisterUserRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone is required")
    // @NotBlank checks: not null, not empty "", not whitespace "   "
    private String phone;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Role is required")
    // @NotNull checks: not null (but doesn't check empty string)
    private UserRole role;

    private String preferredLanguage; // optional — no validation annotation

    // Standard getters and setters (needed by Jackson for JSON deserialization)
}
```

---

### `interfaces/rest/dto/AuthResponse.java` — Outbound DTO

```java
// This is what callers receive back
public class AuthResponse {
    private final Long userId;
    private final String role;
    private final String token;  // JWT access token — use in future requests as: Authorization: Bearer <token>

    public AuthResponse(Long userId, String role, String token) {
        this.userId = userId;
        this.role = role;
        this.token = token;
    }

    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public String getToken() { return token; }
}
```

---

### `interfaces/rest/GlobalExceptionHandler.java`

**What is it?** A central place that intercepts every exception thrown anywhere in the app and converts it into a proper HTTP response with a meaningful status code.

```java
@RestControllerAdvice  // applies to all @RestController classes in the app
public class GlobalExceptionHandler {

    // When RegisterUserUseCase throws PhoneAlreadyRegisteredException:
    @ExceptionHandler(PhoneAlreadyRegisteredException.class)
    @ResponseStatus(HttpStatus.CONFLICT)  // HTTP 409
    public Map<String, String> handlePhoneAlreadyRegistered(PhoneAlreadyRegisteredException ex) {
        return Map.of("error", ex.getMessage());
        // Response body: { "error": "Phone already registered: +966501234567" }
    }

    // When LoginUserUseCase throws InvalidCredentialsException:
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)  // HTTP 401
    public Map<String, String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return Map.of("error", ex.getMessage());
        // Response body: { "error": "Invalid credentials" }
    }

    // Catch-all for any DomainException not handled above:
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // HTTP 400
    public Map<String, String> handleDomainException(DomainException ex) {
        return Map.of("error", ex.getMessage());
    }

    // When Value Object constructors throw (invalid phone, blank name):
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // HTTP 400
    public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    // When @Valid annotations fail (missing fields, blank values):
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)  // HTTP 400
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");

        // Collect per-field errors
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        body.put("fields", fieldErrors);

        // Response: { "error": "Validation failed", "fields": { "phone": "Phone is required" } }
        return body;
    }

    // When Keycloak is down or throws an unrecoverable error:
    @ExceptionHandler(IdentityProviderService.IdentityProviderException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)  // HTTP 503
    public Map<String, String> handleProviderError(IdentityProviderService.IdentityProviderException ex) {
        return Map.of("error", "Identity provider error: " + ex.getMessage());
    }
}
```

---

## 9. Database Schema & Migrations

**What is Flyway?** A tool that automatically runs SQL migration scripts when the app starts. Scripts run in order (V1, V2, V3...) and are never run twice. This keeps the DB schema in sync with the code.

### Migration Files

**`V1__init.sql`** — Initial schema
```sql
CREATE TABLE users (
    id           BIGSERIAL PRIMARY KEY,     -- auto-incrementing Long
    phone        VARCHAR(20) NOT NULL UNIQUE, -- unique login identifier
    password_hash VARCHAR(255) NOT NULL,    -- BCrypt hash (60 chars)
    role         VARCHAR(30) NOT NULL,      -- "CUSTOMER", "PROVIDER", "ADMIN"
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**`V2__add_user_profile_columns.sql`** — Added profile fields
```sql
ALTER TABLE users
    ADD COLUMN full_name          VARCHAR(100),
    ADD COLUMN preferred_language VARCHAR(10),
    ADD COLUMN updated_at         TIMESTAMP;
```

**`V3__make_full_name_not_null.sql`** — Made full_name required
```sql
UPDATE users SET full_name = 'Unknown' WHERE full_name IS NULL; -- migrate existing rows first
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
```

**`V4__make_password_hash_nullable.sql`** — Keycloak integration
```sql
-- After switching to Keycloak, passwords are stored in Keycloak, not our DB
-- We store "KEYCLOAK_MANAGED" as a placeholder
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash SET DEFAULT 'KEYCLOAK_MANAGED';
```

### Final Schema
```
users
├── id                BIGSERIAL   PK, auto-increment
├── full_name         VARCHAR(100) NOT NULL
├── phone             VARCHAR(20)  NOT NULL UNIQUE  ← login identifier
├── password_hash     VARCHAR(255) DEFAULT 'KEYCLOAK_MANAGED'
├── role              VARCHAR(30)  NOT NULL  (CUSTOMER / PROVIDER / ADMIN)
├── preferred_language VARCHAR(10) nullable
├── created_at        TIMESTAMP   NOT NULL  DEFAULT NOW()
└── updated_at        TIMESTAMP   NOT NULL
```

---

## 10. Configuration Files

### `application.yml` — Local Development

```yaml
spring:
  application:
    name: identity-service          # used in logs and service discovery

  datasource:
    url: jdbc:postgresql://localhost:5433/identity_db  # port 5433 (not default 5432!)
    username: app
    password: app

  jpa:
    hibernate:
      ddl-auto: validate            # Hibernate checks schema matches entities but doesn't change it
                                    # Flyway manages schema changes — not Hibernate

  flyway:
    enabled: true                   # run migrations on startup

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/glowzi
          # Spring downloads Keycloak's public key from this URL to verify JWT signatures

server:
  port: 8081                        # this service runs on 8081

keycloak:
  base-url: http://localhost:9090
  realm: glowzi
  client-id: glowzi-app             # the OAuth2 client registered in Keycloak
  client-secret: glowzi-app-secret
  admin:
    username: admin                 # Keycloak admin credentials for user management
    password: admin

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics  # exposed actuator endpoints
```

### `application-docker.yml` — Docker/Production

This profile activates when running in Docker. It reads values from environment variables instead of hardcoded values, which is the correct way to configure production services.

```yaml
spring:
  datasource:
    url: ${DB_URL}           # set in docker-compose.yml
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  logging:
    structured:
      format:
        console: ecs          # JSON structured logs (better for log aggregators like ELK)

keycloak:
  base-url: ${KEYCLOAK_URL}
  realm: ${KEYCLOAK_REALM}
  client-id: ${KEYCLOAK_CLIENT_ID}
  client-secret: ${KEYCLOAK_CLIENT_SECRET}
  admin:
    username: ${KEYCLOAK_ADMIN_USERNAME}
    password: ${KEYCLOAK_ADMIN_PASSWORD}
```

---

## 11. Test Suite Explained

The test suite has **3 levels** following the testing pyramid:

```
         /\
        /  \     ← Integration tests (AuthIntegrationTest, UserRepositoryAdapterTest)
       /----\       Slower, need DB (Testcontainers), test the full stack
      /      \
     /--------\  ← Controller tests (AuthControllerTest)
    /          \    Fast, no DB, test HTTP layer with mocked use cases
   /------------\
  /              \ ← Unit tests (domain + application)
 /----------------\  Fastest, no Spring, no DB, test pure logic
```

### Unit Tests — Domain Layer
| File | What it Tests |
|------|--------------|
| `PhoneTest` | Valid E.164 accepted, null/empty/missing-plus/too-short rejected, equality by value |
| `FullNameTest` | Valid name accepted, null/blank/over-100-chars rejected, equality |
| `HashedPasswordTest` | Valid hash accepted, null/blank rejected, toString() masks the value |
| `UserTest` | `User.register()` creates correct fields, emits `UserRegisteredEvent`, clearDomainEvents works, null role rejected, `passwordMatches()` works |

### Unit Tests — Application Layer
| File | What it Tests |
|------|--------------|
| `RegisterUserUseCaseTest` | Happy path returns token, duplicate phone throws, password is hashed before save |
| `LoginUserUseCaseTest` | Happy path returns token, unknown phone throws, wrong password throws |
| `BCryptPasswordHasherTest` | Returns BCrypt format, matches correct password, rejects wrong password, salted (two hashes differ) |
| `JwtServiceImplTest` | `generateToken()` throws UnsupportedOperationException, `getToken()` delegates to Keycloak |

### Controller Tests — HTTP Layer
| File | What it Tests |
|------|--------------|
| `AuthControllerTest` | All register scenarios (201, 409, 400 blank name/phone/password/role, invalid phone, PROVIDER role), all login scenarios (200, 401 bad credentials, 400 blank fields) |

### Integration Tests — Full Stack
| File | What it Tests |
|------|--------------|
| `AuthIntegrationTest` | Full register flow with real DB, register→login flow, duplicate phone 409, wrong password 401, unregistered phone 401, ADMIN role |
| `UserRepositoryAdapterTest` | save() returns ID, findByPhone() found/not-found, existsByPhone() true/false, full round-trip domain→DB→domain mapping |

### Architecture Tests
| File | What it Tests |
|------|--------------|
| `HexagonalArchitectureTest` | domain/ never imports application/, infrastructure/, interfaces/, Spring, or JPA. application/ never imports interfaces/ or infrastructure/. |

---

## 12. Problems Solved (Changelog)

### Issue 1 — Empty `DomainEventPublisher.java`
`DomainEventPublisher.java` existed as an empty class with no code.

**Fix**:
- Converted to a proper `interface` (port) with `publish(DomainEvent)` and `publishAll(List<DomainEvent>)`
- Created `SpringDomainEventPublisher.java` in `infrastructure/event/` implementing it via Spring's `ApplicationEventPublisher`
- Updated `RegisterUserUseCase` to inject and call it after save

### Issue 2 — `RegisterUserUseCase` missing `DomainEventPublisher`
After fixing the publisher, `RegisterUserUseCase` did not inject or use it.

**Fix**: Added `DomainEventPublisher eventPublisher` constructor parameter, published events after save, cleared them to prevent double-publishing.

### Issue 3 — DB schema out of sync
The domain `User` had `fullName`, `preferredLanguage`, `updatedAt` but early migrations didn't have them.

**Fix**: Added V2, V3, V4 migration files.

### Issue 4 — Keycloak integration replaced local JWT
After switching from local JWT (JJWT) to Keycloak, `JwtServiceImpl.generateToken()` became unused.

**Status**: Left as `UnsupportedOperationException` with a clear message. Future cleanup: remove `generateToken()` from the `JwtService` port entirely.

---

## 13. API Reference

### `POST /auth/register`

**Request:**
```json
{
  "fullName": "Ahmed Ali",
  "phone": "+966501234567",
  "password": "SecurePass1",
  "role": "CUSTOMER",
  "preferredLanguage": "ar"
}
```

**Response 201:**
```json
{
  "userId": 1,
  "role": "CUSTOMER",
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error Responses:**
| Status | Condition | Body |
|--------|-----------|------|
| 400 | Missing/blank fields | `{ "error": "Validation failed", "fields": { "phone": "Phone is required" } }` |
| 400 | Invalid phone format | `{ "error": "Phone must be in E.164 format..." }` |
| 409 | Phone already registered | `{ "error": "Phone already registered: +966501234567" }` |
| 503 | Keycloak unreachable | `{ "error": "Identity provider error: ..." }` |

---

### `POST /auth/login`

**Request:**
```json
{
  "phone": "+966501234567",
  "password": "SecurePass1"
}
```

**Response 200:**
```json
{
  "userId": 1,
  "role": "CUSTOMER",
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Error Responses:**
| Status | Condition | Body |
|--------|-----------|------|
| 400 | Missing/blank fields | `{ "error": "Validation failed", "fields": {...} }` |
| 401 | Wrong phone or password | `{ "error": "Invalid credentials" }` |

**Using the token in subsequent requests:**
```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 14. What is Still Missing (Roadmap)

| Priority | Feature | What to build |
|----------|---------|--------------|
| 🔴 High | `POST /auth/refresh` | Refresh expired token using Keycloak refresh_token grant. Add `RefreshTokenUseCase` + endpoint. |
| 🔴 High | `GET /auth/validate` | Validate token and return user info. Used by API Gateway to verify callers. |
| 🟡 Medium | `POST /auth/logout` | Call Keycloak logout endpoint to revoke token. Add `LogoutUseCase`. |
| 🟡 Medium | `GET /auth/me` | Return current user's profile (id, name, phone, role) from JWT claims. |
| 🟡 Medium | `POST /auth/change-password` | Update password in Keycloak via Admin API. Requires authentication. |
| 🟡 Medium | Password strength validation | Add `@Pattern` on `RegisterUserRequest.password`: min 8 chars, uppercase, lowercase, digit. |
| 🟡 Medium | E.164 phone `@Pattern` on DTO | Add `@Pattern(regexp="^\\+[1-9]\\d{6,14}$")` on `RegisterUserRequest.phone` and `LoginRequest.phone`. |
| 🟡 Medium | CORS configuration | Add `CorsConfigurationSource` bean to `SecurityConfig` for frontend origin. |
| 🟢 Low | Remove broken `generateToken()` | Delete `generateToken(User)` from `JwtService` port and `JwtServiceImpl`. Only `getToken(phone, password)` is used. |
| 🟢 Low | Audit logging | Log all register/login events with timestamp, IP, and outcome to an audit table. |
| 🟢 Low | Rate limiting | Limit login attempts per phone per time window to prevent brute force. |

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
