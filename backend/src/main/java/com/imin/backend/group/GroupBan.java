package com.imin.backend.group;

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
 * Per-group scoped ban — presence of a row means the user is banned from
 * that specific group (unban = delete the row). Every ban check is scoped
 * by {@code (groupId, userId)}, so a ban in one group structurally cannot
 * affect any other group (see spec.md Groups: bans are per-group, not
 * account-wide).
 */
@Entity
@Table(name = "group_bans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GroupBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant bannedAt = Instant.now();

    @Column(name = "banned_by_id", nullable = false)
    private Long bannedById;
}
