package com.glowzi.identity.unit.application;

import com.glowzi.identity.application.JwtService;
import com.glowzi.identity.application.LoginUserUseCase;
import com.glowzi.identity.application.PasswordHasher;
import com.glowzi.identity.application.command.LoginCommand;
import com.glowzi.identity.application.result.AuthResult;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.UserRepository;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.exception.InvalidCredentialsException;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private JwtService jwtService;

    private LoginUserUseCase useCase;

    private static final User EXISTING_USER = new User(
            1L,
            new FullName("Ahmed Ali"),
            new Phone("+966501234567"),
            new HashedPassword("$2a$10$hashed"),
            UserRole.CUSTOMER,
            "ar",
            LocalDateTime.now(),
            LocalDateTime.now()
    );

    @BeforeEach
    void setUp() {
        useCase = new LoginUserUseCase(userRepository, passwordHasher, jwtService);
    }

    @Test
    void should_login_with_correct_credentials() {
        LoginCommand cmd = new LoginCommand("+966501234567", "correctPass");

        when(userRepository.findByPhone(new Phone("+966501234567")))
                .thenReturn(Optional.of(EXISTING_USER));
        when(passwordHasher.matches("correctPass", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken(EXISTING_USER)).thenReturn("jwt-token");

        AuthResult result = useCase.execute(cmd);

        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.role()).isEqualTo("CUSTOMER");
        assertThat(result.token()).isEqualTo("jwt-token");
    }

    @Test
    void should_throw_when_phone_not_found() {
        LoginCommand cmd = new LoginCommand("+966509999999", "pass");

        when(userRepository.findByPhone(any(Phone.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void should_throw_when_password_wrong() {
        LoginCommand cmd = new LoginCommand("+966501234567", "wrongPass");

        when(userRepository.findByPhone(any(Phone.class)))
                .thenReturn(Optional.of(EXISTING_USER));
        when(passwordHasher.matches("wrongPass", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }
}
