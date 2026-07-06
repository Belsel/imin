package com.imin.backend.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    Optional<GroupMembership> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMembership> findByGroupId(Long groupId);

    List<GroupMembership> findByGroupIdOrderByJoinedAtAsc(Long groupId);

    List<GroupMembership> findByUserId(Long userId);

    long countByGroupId(Long groupId);

    long countByGroupIdAndIsAdminTrue(Long groupId);

    void deleteByGroupId(Long groupId);
}
