package com.imin.backend.group.dto;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.group.Group;

import java.util.List;

/**
 * Group summary for the public, unauthenticated landing-page recommendation
 * feed (see specs/public-group-recommendations/spec.md). Deliberately
 * narrower than GroupRecommendationResponse: no distanceKm/
 * matchingCategoryCount (meaningless without a visitor location/preferences)
 * and no createdAt (ranking-internal only, see GroupService#getPublicRecommendations).
 * latitude/longitude are rounded to 2 decimal places here, at response-
 * construction time only -- the stored Group row is never read back,
 * mutated, or persisted with a rounded value.
 */
public record PublicGroupRecommendationResponse(
        Long id,
        String name,
        String description,
        double latitude,
        double longitude,
        long memberCount,
        List<CategoryResponse> categories
) {
    public static PublicGroupRecommendationResponse from(Group group, long memberCount,
                                                          List<CategoryResponse> categories) {
        return new PublicGroupRecommendationResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                round2(group.getLatitude()),
                round2(group.getLongitude()),
                memberCount,
                categories
        );
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
