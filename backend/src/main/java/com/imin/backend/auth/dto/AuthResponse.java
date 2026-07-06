package com.imin.backend.auth.dto;

public record AuthResponse(String token, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String token, long expiresIn) {
        return new AuthResponse(token, "Bearer", expiresIn);
    }
}
