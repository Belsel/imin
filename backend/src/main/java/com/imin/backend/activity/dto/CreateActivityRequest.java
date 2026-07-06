package com.imin.backend.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code latitude}/{@code longitude} are independently optional — spec.md
 * Activities only requires "an optional location," with no rule that one
 * must be present if the other is. Deliberately has no recurrence or
 * RSVP/attendance field of any kind — both are out of scope (see
 * {@link com.imin.backend.activity.Activity} javadoc).
 */
public record CreateActivityRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Instant scheduledTime,
        Double latitude,
        Double longitude
) {}
