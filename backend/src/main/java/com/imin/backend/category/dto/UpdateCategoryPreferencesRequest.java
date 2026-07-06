package com.imin.backend.category.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replaces the caller's full set of category preferences with the given
 * list of category ids (empty list clears all preferences).
 */
public record UpdateCategoryPreferencesRequest(
        @NotNull List<Long> categoryIds
) {}
