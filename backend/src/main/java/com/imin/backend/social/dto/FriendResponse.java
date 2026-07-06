package com.imin.backend.social.dto;

import com.imin.backend.social.Friendship;
import com.imin.backend.user.User;

import java.time.Instant;

public record FriendResponse(
        Long userId,
        String displayName,
        Instant addedAt
) {

    public static FriendResponse from(Friendship friendship, User followee) {
        return new FriendResponse(
                followee.getId(),
                followee.getDisplayName(),
                friendship.getCreatedAt()
        );
    }
}
