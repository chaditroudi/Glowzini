package com.glowzi.identity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glowzi.identity.TestcontainersConfiguration;
import com.glowzi.identity.application.IdentityProviderService;
import com.glowzi.identity.domain.enums.UserRole;
import com.glowzi.identity.interfaces.rest.dto.LoginRequest;
import com.glowzi.identity.interfaces.rest.dto.RegisterUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests using Testcontainers PostgreSQL.
 * KeycloakUserService is mocked (external dependency).
 * Tests the flow: HTTP → Controller → UseCase → Repository → DB → Response.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestcontainersConfiguration.class)
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IdentityProviderService identityProvider;

    private static final String FAKE_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.fake-sig";

    @Test
    @DisplayName("full register flow: POST /auth/register → provider + DB → token response")
    void register_fullFlow_returnsToken() throws Exception {
        when(identityProvider.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-user-id-1");
        when(identityProvider.authenticate(anyString(), anyString()))
                .thenReturn(FAKE_TOKEN);

        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Integration Test User");
        request.setPhone("+966500000001");
        request.setPassword("TestPass123");
        request.setRole(UserRole.CUSTOMER);
        request.setPreferredLanguage("en");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(token).startsWith("eyJ");
    }

    @Test
    @DisplayName("full register → login flow with real DB")
    void registerThenLogin_fullFlow() throws Exception {
        when(identityProvider.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-user-id-2");
        when(identityProvider.authenticate(anyString(), anyString()))
                .thenReturn(FAKE_TOKEN);

        // Register
        RegisterUserRequest regRequest = new RegisterUserRequest();
        regRequest.setFullName("Login Flow User");
        regRequest.setPhone("+966500000002");
        regRequest.setPassword("MyPassword123");
        regRequest.setRole(UserRole.PROVIDER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login with same credentials
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhone("+966500000002");
        loginRequest.setPassword("MyPassword123");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.role").value("PROVIDER"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("duplicate phone registration returns 409")
    void register_duplicatePhone_returns409() throws Exception {
        when(identityProvider.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-user-id-3");
        when(identityProvider.authenticate(anyString(), anyString()))
                .thenReturn(FAKE_TOKEN);

        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("First User");
        request.setPhone("+966500000003");
        request.setPassword("Password123");
        request.setRole(UserRole.CUSTOMER);

        // First registration succeeds
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same phone fails (local DB check)
        request.setFullName("Second User");
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Phone already registered: +966500000003"));
    }

    @Test
    @DisplayName("login with bad credentials returns 401 (provider rejects)")
    void login_wrongPassword_returns401() throws Exception {
        when(identityProvider.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-user-id-4");
        when(identityProvider.authenticate(eq("+966500000004"), eq("CorrectPassword")))
                .thenReturn(FAKE_TOKEN);
        when(identityProvider.authenticate(eq("+966500000004"), eq("WrongPassword")))
                .thenThrow(new IdentityProviderService.IdentityProviderAuthException("Authentication failed"));

        // Register first
        RegisterUserRequest regRequest = new RegisterUserRequest();
        regRequest.setFullName("Wrong Pass User");
        regRequest.setPhone("+966500000004");
        regRequest.setPassword("CorrectPassword");
        regRequest.setRole(UserRole.CUSTOMER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login with wrong password
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhone("+966500000004");
        loginRequest.setPassword("WrongPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("login with unregistered phone returns 401")
    void login_unregisteredPhone_returns401() throws Exception {
        when(identityProvider.authenticate(anyString(), anyString()))
                .thenThrow(new IdentityProviderService.IdentityProviderAuthException("Authentication failed"));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhone("+966500000099");
        loginRequest.setPassword("AnyPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("invalid phone format returns 400")
    void register_invalidPhoneFormat_returns400() throws Exception {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Bad Phone User");
        request.setPhone("0501234567");
        request.setPassword("Password123");
        request.setRole(UserRole.CUSTOMER);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("register ADMIN role works correctly")
    void register_adminRole_success() throws Exception {
        when(identityProvider.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("kc-user-id-admin");
        when(identityProvider.authenticate(anyString(), anyString()))
                .thenReturn(FAKE_TOKEN);

        RegisterUserRequest request = new RegisterUserRequest();
        request.setFullName("Admin User");
        request.setPhone("+966500000005");
        request.setPassword("AdminPass123");
        request.setRole(UserRole.ADMIN);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
