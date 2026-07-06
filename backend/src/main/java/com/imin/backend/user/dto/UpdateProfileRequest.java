package com.imin.backend.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial-update semantics: both fields are optional/nullable, and a field
 * that is omitted (null) is left untouched on the user entity. {@code bio}
 * additionally treats an explicit empty string as "clear the bio", per
 * spec.md's "set, edit, and clear" wording for the biography field.
 * {@code displayName}, by contrast, can never be cleared to blank — every
 * user has one set at registration (see {@code RegisterRequest}) and the
 * spec only allows changing it, not removing it. A present-but-blank
 * {@code displayName} is rejected, but that check happens in
 * {@link com.imin.backend.user.UserService} rather than via {@code @NotBlank}
 * here: Bean Validation's {@code @NotBlank} rejects {@code null} too, which
 * would make {@code displayName} a required field on every PATCH instead of
 * an optional one — the opposite of the partial-update semantics this DTO
 * needs.
 */
public record UpdateProfileRequest(
        String displayName,
        @Size(max = 2000) String bio
) {}
