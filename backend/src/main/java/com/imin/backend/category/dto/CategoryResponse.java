package com.imin.backend.category.dto;

import com.imin.backend.category.GroupCategory;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(GroupCategory category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
