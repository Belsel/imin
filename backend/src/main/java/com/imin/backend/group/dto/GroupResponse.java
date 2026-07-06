package com.imin.backend.group.dto;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.group.Group;

import java.time.Instant;
import java.util.List;

public record GroupResponse(
        Long id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        Instant createdAt,
        long memberCount,
        boolean isAdmin,
        boolean isMember,
        List<CategoryResponse> categories
) {

    public static GroupResponse from(Group group, long memberCount, boolean isAdmin, boolean isMember,
                                      List<CategoryResponse> categories) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getLatitude(),
                group.getLongitude(),
                group.getCreatedAt(),
                memberCount,
                isAdmin,
                isMember,
                categories
        );
    }
}
