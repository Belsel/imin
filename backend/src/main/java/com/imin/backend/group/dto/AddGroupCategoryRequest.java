package com.imin.backend.group.dto;

import jakarta.validation.constraints.NotNull;

public record AddGroupCategoryRequest(
        @NotNull Long categoryId
) {}
