package com.imin.backend.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserCategoryPreferenceRepository extends JpaRepository<UserCategoryPreference, Long> {

    List<UserCategoryPreference> findByUserId(Long userId);

    Optional<UserCategoryPreference> findByUserIdAndCategoryId(Long userId, Long categoryId);

    void deleteByUserId(Long userId);

    List<UserCategoryPreference> findByCategoryIdIn(List<Long> categoryIds);
}
