package com.imin.backend.chat.dto;

import com.imin.backend.chat.DirectMessage;
import com.imin.backend.user.User;

import java.time.Instant;

public record DirectMessageResponse(
        Long id,
        Long threadId,
        Long senderId,
        String senderDisplayName,
        String body,
        Instant createdAt
) {

    public static DirectMessageResponse from(DirectMessage message, User sender) {
        return new DirectMessageResponse(
                message.getId(),
                message.getThreadId(),
                message.getSenderId(),
                sender == null ? null : sender.getDisplayName(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
