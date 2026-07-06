package com.imin.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single text message within a {@link DirectThread}. Mirrors
 * {@link GroupChatMessage}'s shape and id-cursor polling convention exactly
 * (see {@link DirectChatService}) for consistency between group chat and
 * direct chat. Text-only by design — no attachment/file/url field of any
 * kind, per spec.md Direct chats ("no file, image, or other attachment
 * types in MVP") — do not add one.
 */
@Entity
@Table(name = "direct_messages")
@Getter
@Setter
@NoArgsConstructor
public class DirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "thread_id", nullable = false)
    private Long threadId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
