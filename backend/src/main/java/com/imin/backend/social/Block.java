package com.imin.backend.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A one-directional block: presence of a row means {@code blockerId} has
 * blocked {@code blockedId}. Unblocking = delete the row.
 *
 * <p>Blocking does not delete or otherwise touch any {@link Friendship} row
 * in either direction — the two relations are independent facts (see
 * spec.md Resolved Questions item 3). The only consumer of this table
 * outside this package is {@link com.imin.backend.chat.DirectChatService},
 * which checks it at message-send time only (never at read/poll time — see
 * that class's javadoc).
 */
@Entity
@Table(name = "blocks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who initiated the block. */
    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    /** The user who was blocked. */
    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
