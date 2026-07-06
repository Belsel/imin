package com.imin.backend.group;

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
 * A group's {@code latitude}/{@code longitude} are captured once, from the
 * creating user's device geolocation, at the moment of creation (see
 * spec.md Groups + design.md §1.3). This is an immutable snapshot — no
 * admin edit/refresh path exists for it, by deliberate omission from
 * {@code GroupController#updateGroup} (rename/description only).
 */
@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
