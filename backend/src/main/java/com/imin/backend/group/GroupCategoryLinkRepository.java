package com.imin.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupCategoryLinkRepository extends JpaRepository<GroupCategoryLink, Long> {

    List<GroupCategoryLink> findByGroupId(Long groupId);

    List<GroupCategoryLink> findByGroupIdIn(List<Long> groupIds);

    Optional<GroupCategoryLink> findByGroupIdAndCategoryId(Long groupId, Long categoryId);

    void deleteByGroupId(Long groupId);

    void deleteByGroupIdAndCategoryId(Long groupId, Long categoryId);
}
