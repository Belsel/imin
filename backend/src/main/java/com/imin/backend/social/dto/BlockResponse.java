package com.imin.backend.social.dto;

import com.imin.backend.social.Block;
import com.imin.backend.user.User;

import java.time.Instant;

public record BlockResponse(
        Long userId,
        String displayName,
        Instant blockedAt
) {

    public static BlockResponse from(Block block, User blocked) {
        return new BlockResponse(
                blocked.getId(),
                blocked.getDisplayName(),
                block.getCreatedAt()
        );
    }
}
