package com.imin.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin-only rename/description update. Deliberately has no
 * latitude/longitude fields — a group's location is an immutable
 * creation-time snapshot with no edit/refresh path (see spec.md Groups,
 * design.md §1.3).
 */
public record UpdateGroupRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description
) {}
