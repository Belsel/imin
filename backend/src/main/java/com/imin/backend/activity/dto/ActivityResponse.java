package com.imin.backend.activity.dto;

import com.imin.backend.activity.Activity;
import com.imin.backend.user.User;

import java.time.Instant;

public record ActivityResponse(
        Long id,
        Long groupId,
        Long ownerId,
        String ownerDisplayName,
        String name,
        String description,
        Instant scheduledTime,
        Double latitude,
        Double longitude,
        Instant createdAt
) {

    public static ActivityResponse from(Activity activity, User owner) {
        return new ActivityResponse(
                activity.getId(),
                activity.getGroupId(),
                activity.getOwnerId(),
                owner == null ? null : owner.getDisplayName(),
                activity.getName(),
                activity.getDescription(),
                activity.getScheduledTime(),
                activity.getLatitude(),
                activity.getLongitude(),
                activity.getCreatedAt()
        );
    }
}
