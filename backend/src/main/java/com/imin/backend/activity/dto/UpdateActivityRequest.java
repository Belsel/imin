package com.imin.backend.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Owner-or-admin edit of name/description/scheduledTime/location — see
 * {@link com.imin.backend.activity.ActivityService#updateActivity}. Same
 * independently-optional {@code latitude}/{@code longitude} shape as
 * {@link CreateActivityRequest}; no recurrence/RSVP field exists to edit.
 */
public record UpdateActivityRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Instant scheduledTime,
        Double latitude,
        Double longitude
) {}
