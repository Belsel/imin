package com.imin.backend.group.dto;

import com.imin.backend.group.GroupBan;
import com.imin.backend.user.User;

import java.time.Instant;

public record GroupBanResponse(
        Long userId,
        String displayName,
        Instant bannedAt,
        Long bannedById
) {

    public static GroupBanResponse from(GroupBan ban, User bannedUser) {
        return new GroupBanResponse(
                bannedUser.getId(),
                bannedUser.getDisplayName(),
                ban.getBannedAt(),
                ban.getBannedById()
        );
    }
}
