package com.imin.backend.category;

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

/**
 * Join row recording that a user has selected a given {@link GroupCategory}
 * as a preference, used to drive group recommendations (see
 * com.imin.backend.group.GroupService#recommendGroups). Plain
 * {@code @Entity}, not {@code @ElementCollection}, so it has its own id and
 * is easy to query directly (consistent with design.md §1.1's stated
 * preference for queryable join entities over JPA collection mappings).
 */
@Entity
@Table(name = "user_category_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserCategoryPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;
}
