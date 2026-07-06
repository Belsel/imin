package com.imin.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code latitude}/{@code longitude} are the creator's current-location
 * coordinates, obtained client-side via the browser Geolocation API and
 * sent up as part of this same request — there is no manual/optional
 * location field on the create-group form (see spec.md Groups, design.md
 * §1.3). The backend stores exactly what it receives and never recomputes
 * or accepts later updates to it.
 */
public record CreateGroupRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Double latitude,
        @NotNull Double longitude,
        List<Long> categoryIds
) {}
