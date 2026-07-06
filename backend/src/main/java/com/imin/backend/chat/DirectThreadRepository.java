package com.imin.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DirectThreadRepository extends JpaRepository<DirectThread, Long> {

    /** Callers must pass ids already normalized so {@code userAId < userBId}. */
    Optional<DirectThread> findByUserAIdAndUserBId(Long userAId, Long userBId);

    List<DirectThread> findByUserAIdOrUserBId(Long userAId, Long userBId);
}
