package com.imin.backend.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /** A group's calendar, chronologically sorted — see {@link ActivityService}. */
    List<Activity> findByGroupIdOrderByScheduledTimeAsc(Long groupId);

    void deleteByGroupId(Long groupId);
}
