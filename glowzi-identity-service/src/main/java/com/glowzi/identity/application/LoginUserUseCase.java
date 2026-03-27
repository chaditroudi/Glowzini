package com.glowzi.identity.application;

import com.glowzi.identity.application.command.LoginCommand;
import com.glowzi.identity.application.result.AuthResult;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.domain.exception.InvalidCredentialsException;
import com.glowzi.identity.domain.vo.Phone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application Service — orchestrates user login via identity provider.
 *
 * Flow:
 * 1. Validate phone format
 * 2. Authenticate against identity provider (Keycloak)
 * 3. Look up local user for app-specific data
 * 4. Return AuthResult with provider-issued token
 */
@Service
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final IdentityProviderService identityProvider;

    public LoginUserUseCase(UserRepository userRepository,
                            IdentityProviderService identityProvider) {
        this.userRepository = userRepository;
        this.identityProvider = identityProvider;
    }

    @Transactional(readOnly = true)
    public AuthResult execute(LoginCommand command) {

        Phone phone = new Phone(command.phone());
        String username = phone.value();

        // 1. Authenticate via identity provider
        String token;
        try {
            token = identityProvider.authenticate(username, command.rawPassword());
        } catch (IdentityProviderService.IdentityProviderAuthException e) {
            throw new InvalidCredentialsException();
        }

        // 2. Find local user for app-specific data
        User user = userRepository.findByPhone(phone)
                .orElseThrow(InvalidCredentialsException::new);

        // 3. Return with provider-issued token
        return new AuthResult(user.getId(), user.getRole().name(), token);
    }
}
