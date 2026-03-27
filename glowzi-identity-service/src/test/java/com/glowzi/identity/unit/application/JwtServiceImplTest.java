package com.glowzi.identity.unit.application;

import com.glowzi.identity.application.IdentityProviderService;
import com.glowzi.identity.domain.User;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.domain.vo.FullName;
import com.glowzi.identity.domain.vo.HashedPassword;
import com.glowzi.identity.domain.vo.Phone;
import com.glowzi.identity.infrastructure.security.JwtServiceImpl;
import com.glowzi.identity.infrastructure.security.KeycloakUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JwtServiceImpl — Keycloak token delegation.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceImplTest {

    @Mock
    private KeycloakUserService keycloakUserService;

    private JwtServiceImpl jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(keycloakUserService);
    }

    @Test
    @DisplayName("generateToken(User) throws UnsupportedOperationException")
    void generateToken_throwsUnsupported() {
        User user = createTestUser(1L, "+966501234567", UserRole.CUSTOMER);

        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getToken delegates to KeycloakUserService and returns access token")
    void getToken_delegatesToKeycloak() {
        AccessTokenResponse tokenResponse = new AccessTokenResponse();
        tokenResponse.setToken("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test.signature");

        when(keycloakUserService.getToken("+966501234567", "SecurePass1"))
                .thenReturn(tokenResponse);

        String token = jwtService.getToken("+966501234567", "SecurePass1");

        assertThat(token).isEqualTo("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test.signature");
    }

    @Test
    @DisplayName("getToken propagates KeycloakAuthException on bad credentials")
    void getToken_propagatesAuthException() {
        when(keycloakUserService.getToken("+966501234567", "wrong"))
                .thenThrow(new IdentityProviderService.IdentityProviderAuthException("Authentication failed"));

        assertThatThrownBy(() -> jwtService.getToken("+966501234567", "wrong"))
                .isInstanceOf(IdentityProviderService.IdentityProviderAuthException.class);
    }

    private User createTestUser(Long id, String phone, UserRole role) {
        return new User(
                id,
                new FullName("Test User"),
                new Phone(phone),
                new HashedPassword("KEYCLOAK_MANAGED"),
                role,
                "en",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
