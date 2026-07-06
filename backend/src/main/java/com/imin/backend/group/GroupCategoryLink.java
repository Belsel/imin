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

/**
 * Join row: a {@link Group} tagged with one of the fixed
 * {@code GroupCategory} taxonomy entries. Many-to-many via id columns only
 * (no JPA collection navigation), consistent with the rest of this slice's
 * join entities.
 */
@Entity
@Table(name = "group_category_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "category_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GroupCategoryLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;
}
