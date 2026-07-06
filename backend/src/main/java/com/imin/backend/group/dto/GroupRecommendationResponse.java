package com.imin.backend.group.dto;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.group.Group;

import java.time.Instant;
import java.util.List;

/**
 * A recommended group, annotated with the inputs that drove its ranking
 * (distance from the caller's supplied location, and how many of the
 * group's categories overlap with the caller's selected preferences) so
 * the frontend can explain the recommendation if it wants to. See
 * {@code GroupService#recommendGroups} for the scoring approach.
 */
public record GroupRecommendationResponse(
        Long id,
        String name,
        String description,
        Double latitude,
        Double longitude,
        Instant createdAt,
        long memberCount,
        List<CategoryResponse> categories,
        double distanceKm,
        int matchingCategoryCount
) {

    public static GroupRecommendationResponse from(Group group, long memberCount,
                                                     List<CategoryResponse> categories,
                                                     double distanceKm, int matchingCategoryCount) {
        return new GroupRecommendationResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getLatitude(),
                group.getLongitude(),
                group.getCreatedAt(),
                memberCount,
                categories,
                distanceKm,
                matchingCategoryCount
        );
    }
}
