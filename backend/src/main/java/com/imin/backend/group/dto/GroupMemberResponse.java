package com.imin.backend.group.dto;

import com.imin.backend.group.GroupMembership;
import com.imin.backend.user.User;

import java.time.Instant;

public record GroupMemberResponse(
        Long userId,
        String displayName,
        boolean isAdmin,
        Instant joinedAt
) {

    public static GroupMemberResponse from(GroupMembership membership, User user) {
        return new GroupMemberResponse(
                user.getId(),
                user.getDisplayName(),
                membership.isAdmin(),
                membership.getJoinedAt()
        );
    }
}
