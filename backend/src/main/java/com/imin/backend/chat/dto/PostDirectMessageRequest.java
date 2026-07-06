package com.imin.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plain text only — deliberately no attachment/file/url field, per spec.md
 * Direct chats ("no file, image, or other attachment types in MVP"). Do not
 * add one here. Mirrors {@code PostGroupChatMessageRequest} exactly.
 */
public record PostDirectMessageRequest(
        @NotBlank @Size(max = 4000) String body
) {}
