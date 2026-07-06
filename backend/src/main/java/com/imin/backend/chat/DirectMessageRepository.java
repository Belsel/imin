package com.imin.backend.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /** Most recent page, newest first — caller reverses to chronological order before returning. */
    List<DirectMessage> findByThreadIdOrderByIdDesc(Long threadId, Pageable pageable);

    /** Polling query: everything strictly after the given cursor id, oldest first, for incremental append. */
    List<DirectMessage> findByThreadIdAndIdGreaterThanOrderByIdAsc(Long threadId, Long afterId);

    /** Latest message in a thread, for thread-list previews. */
    Optional<DirectMessage> findFirstByThreadIdOrderByIdDesc(Long threadId);
}
