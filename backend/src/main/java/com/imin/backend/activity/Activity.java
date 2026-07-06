package com.imin.backend.activity;

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
 * A single, one-off entry on a group's activity calendar (see spec.md
 * Activities, design.md §1.6). {@code ownerId} is the member who created the
 * activity — the activity's owner can edit it, and any admin of
 * {@code groupId}'s group can also edit or delete it (see
 * {@link ActivityService}).
 *
 * <p>{@code latitude}/{@code longitude} are independently nullable —
 * location is optional, and there is no pairing rule requiring both or
 * neither to be set (see spec.md Activities: "an optional location").
 *
 * <p>Deliberately has no recurrence field and no RSVP/attendance
 * table/column of any kind — both are explicitly out of scope per spec.md
 * Activities ("do not support recurrence... and do not track
 * RSVP/attendance status in MVP"). Do not add either.
 */
@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "scheduled_time", nullable = false)
    private Instant scheduledTime;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
