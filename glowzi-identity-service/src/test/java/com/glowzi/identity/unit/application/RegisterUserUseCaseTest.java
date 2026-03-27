package com.glowzi.identity.unit.application;

import com.glowzi.identity.application.JwtService;
import com.glowzi.identity.application.PasswordHasher;
import com.glowzi.identity.application.RegisterUserUseCase;
import com.glowzi.identity.application.command.RegisterUserCommand;
import com.glowzi.identity.application.result.AuthResult;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.exception.PhoneAlreadyRegisteredException;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test for RegisterUserUseCase.
 * Uses Mockito to mock all ports — no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private JwtService jwtService;

    private RegisterUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RegisterUserUseCase(userRepository, passwordHasher, jwtService);
    }

    @Test
    void should_register_new_user_and_return_token() {
        // Given
        RegisterUserCommand cmd = new RegisterUserCommand(
                "Ahmed Ali", "+966501234567", "SecurePass1", "CUSTOMER", "ar");

        when(userRepository.existsByPhone(any(Phone.class))).thenReturn(false);
        when(passwordHasher.hash("SecurePass1"))
                .thenReturn(new HashedPassword("$2a$10$hashed"));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            // Simulate DB assigning an ID
            return new User(1L, u.getFullName(), u.getPhone(), u.getPasswordHash(),
                    u.getRole(), u.getPreferredLanguage(), u.getCreatedAt(), u.getUpdatedAt());
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");

        // When
        AuthResult result = useCase.execute(cmd);

        // Then
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.role()).isEqualTo("CUSTOMER");
        assertThat(result.token()).isEqualTo("jwt-token");

        // Verify interactions
        verify(userRepository).existsByPhone(new Phone("+966501234567"));
        verify(passwordHasher).hash("SecurePass1");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(User.class));
    }

    @Test
    void should_throw_when_phone_already_registered() {
        RegisterUserCommand cmd = new RegisterUserCommand(
                "Ahmed", "+966501234567", "pass", "CUSTOMER", null);

        when(userRepository.existsByPhone(any(Phone.class))).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(PhoneAlreadyRegisteredException.class)
                .hasMessageContaining("+966501234567");

        verify(userRepository, never()).save(any());
    }

    @Test
    void should_hash_password_before_creating_user() {
        RegisterUserCommand cmd = new RegisterUserCommand(
                "Ahmed", "+966501234567", "rawPassword", "CUSTOMER", null);

        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordHasher.hash("rawPassword"))
                .thenReturn(new HashedPassword("$2a$10$hashed"));
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new User(1L, u.getFullName(), u.getPhone(), u.getPasswordHash(),
                    u.getRole(), u.getPreferredLanguage(), u.getCreatedAt(), u.getUpdatedAt());
        });
        when(jwtService.generateToken(any())).thenReturn("token");

        useCase.execute(cmd);

        // Capture the saved user and verify hash was used
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash().value()).isEqualTo("$2a$10$hashed");
    }
}
