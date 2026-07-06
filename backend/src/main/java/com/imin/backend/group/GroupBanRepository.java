package com.imin.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupBanRepository extends JpaRepository<GroupBan, Long> {

    Optional<GroupBan> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupBan> findByGroupId(Long groupId);

    List<GroupBan> findByUserId(Long userId);

    void deleteByGroupId(Long groupId);
}
