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
 * Tracks which users belong to which groups. {@code joinedAt} is the tenure
 * clock used by the admin-succession rule (earliest {@code joinedAt} wins,
 * see {@link GroupService}). Row is deleted outright on leave/kick/ban —
 * bans are tracked separately in {@link GroupBan} so a ban can exist (and
 * block rejoining) independently of any membership row.
 */
@Entity
@Table(name = "group_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean isAdmin = false;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();
}
