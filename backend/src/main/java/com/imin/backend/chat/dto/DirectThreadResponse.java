package com.imin.backend.chat.dto;

import com.imin.backend.chat.DirectThread;
import com.imin.backend.user.User;

import java.time.Instant;

/**
 * Thread-list row: the other participant's info plus a simple last-message
 * preview (kept minimal for MVP per the task's "keep it simple" steer — no
 * unread counts or read-receipts, neither of which the spec requires).
 */
public record DirectThreadResponse(
        Long threadId,
        Long otherUserId,
        String otherUserDisplayName,
        String lastMessageBody,
        Instant lastMessageAt,
        Instant createdAt
) {

    public static DirectThreadResponse from(DirectThread thread, User otherUser,
                                             String lastMessageBody, Instant lastMessageAt) {
        return new DirectThreadResponse(
                thread.getId(),
                otherUser == null ? null : otherUser.getId(),
                otherUser == null ? null : otherUser.getDisplayName(),
                lastMessageBody,
                lastMessageAt,
                thread.getCreatedAt()
        );
    }
}
