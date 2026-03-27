package com.glowzi.identity.interfaces.rest.dto;

public class AuthResponse {
    private Long userId;
    private String role;
    private String token;

    public AuthResponse(Long userId, String role, String token) {
        this.userId = userId;
        this.role = role;
        this.token = token;
    }

    public Long getUserId() { return userId; }
    public String getRole() { return role; }
    public String getToken() { return token; }
}

