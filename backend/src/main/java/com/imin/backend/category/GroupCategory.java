package com.imin.backend.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fixed, developer-curated taxonomy of group categories (see spec.md
 * Groups: "users and groups cannot create, rename, or otherwise manage
 * categories themselves"). Rows are seeded once at startup by
 * {@link CategorySeeder}; there is deliberately no create/update/delete API
 * for this entity.
 */
@Entity
@Table(name = "group_categories")
@Getter
@Setter
@NoArgsConstructor
public class GroupCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;
}
