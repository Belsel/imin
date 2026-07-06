package com.imin.backend.chat.dto;

import com.imin.backend.chat.GroupChatMessage;
import com.imin.backend.user.User;

import java.time.Instant;

public record GroupChatMessageResponse(
        Long id,
        Long groupId,
        Long senderId,
        String senderDisplayName,
        String body,
        Instant createdAt
) {

    public static GroupChatMessageResponse from(GroupChatMessage message, User sender) {
        return new GroupChatMessageResponse(
                message.getId(),
                message.getGroupId(),
                message.getSenderId(),
                sender == null ? null : sender.getDisplayName(),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
