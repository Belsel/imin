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
 * A one-directional "follow"-style friend-add: presence of a row means
 * {@code followerId} has added {@code followeeId} as a friend. There is no
 * accept/confirm step and no implied reciprocity — {@code followerId} adding
 * {@code followeeId} says nothing about whether {@code followeeId} has
 * separately added {@code followerId} (see spec.md Users, Resolved
 * Questions item 1).
 *
 * <p>Unfriending = delete the row (no soft-delete; the spec has no
 * "history of past friends" requirement). This relationship is standalone in
 * MVP — it does not gate or otherwise affect direct chats or any other
 * feature (see spec.md Resolved Questions item 2's correction); in
 * particular, {@link com.imin.backend.chat.DirectChatService} never queries
 * this table.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "followee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who made the add. */
    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    /** The user who was added. */
    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
