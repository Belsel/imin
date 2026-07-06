package com.imin.backend.chat;

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
 * The single shared conversation between an unordered pair of users (spec.md
 * Direct chats: "at most one ongoing direct conversation between any two
 * given users"). To make the pair unordered while still enforcing "at most
 * one thread per pair" via a plain unique constraint, the pair is normalized
 * at write time so {@code userAId < userBId} (numeric id order) — see
 * {@link DirectChatService#findOrCreateThread} for the lookup-or-create
 * logic that performs this normalization. Callers must never insert with
 * {@code userAId > userBId}.
 */
@Entity
@Table(name = "direct_threads",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_a_id", "user_b_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DirectThread {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_a_id", nullable = false)
    private Long userAId;

    @Column(name = "user_b_id", nullable = false)
    private Long userBId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
