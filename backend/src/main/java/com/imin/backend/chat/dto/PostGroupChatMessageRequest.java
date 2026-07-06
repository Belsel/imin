package com.imin.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plain text only — deliberately no attachment/file/url field, per spec.md
 * Chats ("no file, image, or other attachment types are supported in MVP").
 * Do not add one here.
 */
public record PostGroupChatMessageRequest(
        @NotBlank @Size(max = 4000) String body
) {}
