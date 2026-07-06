package com.imin.backend.activity;

import com.imin.backend.activity.dto.ActivityResponse;
import com.imin.backend.activity.dto.CreateActivityRequest;
import com.imin.backend.activity.dto.UpdateActivityRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A group's activity calendar. Nested under
 * {@code /api/groups/{groupId}/activities}, consistent with
 * {@code GroupChatController}'s nested-resource convention for group-scoped
 * actions.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ResponseEntity<ActivityResponse> createActivity(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable Long groupId,
                                                              @Valid @RequestBody CreateActivityRequest request) {
        return ResponseEntity.ok(activityService.createActivity(jwt.getSubject(), groupId, request));
    }

    @GetMapping
    public ResponseEntity<List<ActivityResponse>> listActivities(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable Long groupId) {
        return ResponseEntity.ok(activityService.listActivities(jwt.getSubject(), groupId));
    }

    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> getActivity(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable Long groupId,
                                                           @PathVariable Long activityId) {
        return ResponseEntity.ok(activityService.getActivity(jwt.getSubject(), groupId, activityId));
    }

    @PatchMapping("/{activityId}")
    public ResponseEntity<ActivityResponse> updateActivity(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable Long groupId,
                                                              @PathVariable Long activityId,
                                                              @Valid @RequestBody UpdateActivityRequest request) {
        return ResponseEntity.ok(activityService.updateActivity(jwt.getSubject(), groupId, activityId, request));
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> deleteActivity(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long groupId,
                                                 @PathVariable Long activityId) {
        activityService.deleteActivity(jwt.getSubject(), groupId, activityId);
        return ResponseEntity.noContent().build();
    }
}
