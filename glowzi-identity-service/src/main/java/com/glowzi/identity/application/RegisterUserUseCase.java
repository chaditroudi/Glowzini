package com.glowzi.identity.application;

import com.glowzi.identity.application.command.RegisterUserCommand;
import com.glowzi.identity.application.result.AuthResult;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.exception.PhoneAlreadyRegisteredException;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service — orchestrates user registration.
 *
 * Flow:
 * 1. Validate input via Value Objects
 * 2. Check phone uniqueness in local DB
 * 3. Create user in identity provider (Keycloak)
 * 4. Save app-specific data in local DB
 * 5. Get token from identity provider
 * 6. Return AuthResult
 */
@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final IdentityProviderService identityProvider;

    public RegisterUserUseCase(UserRepository userRepository,
                               IdentityProviderService identityProvider) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
    }

    @Transactional
    public AuthResult execute(RegisterUserCommand command) {

        // 1. Create Value Objects (self-validating)
        Phone phone = new Phone(command.phone());
        FullName fullName = new FullName(command.fullName());

        // 2. Check uniqueness in local DB
        if (userRepository.existsByPhone(phone)) {
            throw new PhoneAlreadyRegisteredException(phone.value());
        }

        // 3. Create user in identity provider
        UserRole role = UserRole.valueOf(command.role());
        String username = phone.value();

        try {
            identityProvider.createUser(
                    username,
                    fullName.value(),
                    phone.value(),
                    command.rawPassword(),
                    role.name()
            );
        } catch (IdentityProviderService.IdentityProviderConflictException e) {
            throw new PhoneAlreadyRegisteredException(phone.value());
        }

        // 4. Save app-specific data in local DB (password managed by provider)
        HashedPassword placeholder = new HashedPassword("KEYCLOAK_MANAGED");
        User user = User.register(fullName, phone, placeholder, role,
                command.preferredLanguage());
        User saved = userRepository.save(user);

        // 5. Get token from identity provider
        String token = identityProvider.authenticate(username, command.rawPassword());

        // 6. Return
        return new AuthResult(saved.getId(), saved.getRole().name(), token);
    }
}
