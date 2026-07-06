package com.imin.backend.user.dto;

import com.imin.backend.user.User;

import java.time.Instant;

public record ProfileResponse(
        Long id,
        String email,
        String displayName,
        String bio,
        boolean emailVerified,
        Instant createdAt
) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}
