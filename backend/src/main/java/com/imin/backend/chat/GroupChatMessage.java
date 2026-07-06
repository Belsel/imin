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
 * A single text message in a group's chat. There is no separate {@code Chat}
 * entity — a group's chat is just its messages scoped by {@code groupId}
 * (see spec.md Chats, design.md §1.7: "each group has exactly one chat" falls
 * out of messages simply being scoped by {@code groupId}, with no join-table
 * indirection needed).
 *
 * <p>The auto-increment {@code id} doubles as the polling cursor (see
 * {@link GroupChatService} / {@code GET .../messages?after=}) — {@code id}
 * is strictly increasing in insertion order, so {@code id}-based ordering is
 * equivalent to chronological order without depending on clock resolution
 * (two messages persisted within the same millisecond would still sort
 * correctly by id, but not necessarily by a timestamp-only comparison).
 *
 * <p>Text-only by design: this entity has no attachment/file/url field of
 * any kind, per spec.md Chats ("no file, image, or other attachment types
 * are supported in MVP") — do not add one.
 */
@Entity
@Table(name = "group_chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class GroupChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
