package com.imin.backend.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupCategoryRepository extends JpaRepository<GroupCategory, Long> {

    Optional<GroupCategory> findByName(String name);

    boolean existsByName(String name);
}
