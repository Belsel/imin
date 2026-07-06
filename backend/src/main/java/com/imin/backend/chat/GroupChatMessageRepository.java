package com.imin.backend.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {

    /** Most recent page, newest first — caller reverses to chronological order before returning. */
    List<GroupChatMessage> findByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);

    /** Polling query: everything strictly after the given cursor id, oldest first, for incremental append. */
    List<GroupChatMessage> findByGroupIdAndIdGreaterThanOrderByIdAsc(Long groupId, Long afterId);

    void deleteByGroupId(Long groupId);
}
