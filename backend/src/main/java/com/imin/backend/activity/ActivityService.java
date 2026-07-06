package com.imin.backend.activity;

import com.imin.backend.activity.dto.ActivityResponse;
import com.imin.backend.activity.dto.CreateActivityRequest;
import com.imin.backend.activity.dto.UpdateActivityRequest;
import com.imin.backend.group.GroupMembership;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Owns a group's activity calendar: create/list/get/update/delete.
 *
 * <p>Authorization reuses Slice 2's group membership machinery directly
 * ({@link GroupMembershipRepository}) rather than duplicating it, the same
 * way {@link com.imin.backend.chat.GroupChatService} does: any current
 * member may create/view; only the activity's own owner or any current
 * admin of the activity's group may edit or delete. Because a ban always
 * deletes the corresponding {@link GroupMembership} row (see
 * {@code GroupService#banMember}), a banned member has no membership row at
 * all — so "is this user a current member?" already covers both "never
 * joined" and "was banned," with no separate
 * {@link com.imin.backend.group.GroupBanRepository} lookup needed here
 * either, mirroring {@code GroupChatService}'s reasoning exactly.
 *
 * <p>Follows the same 403-for-non-member convention as group chat/member
 * listing (not the 404-for-banned convention used by
 * {@code GroupService#getGroup}) — an activity's group id is already known
 * to the caller, so there's no "confirm banned vs. doesn't exist" ambiguity
 * to protect here.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public ActivityResponse createActivity(String callerEmail, Long groupId, CreateActivityRequest request) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        requireCurrentMember(groupId, caller.getId());

        Activity activity = new Activity();
        activity.setGroupId(groupId);
        activity.setOwnerId(caller.getId());
        activity.setName(request.name());
        activity.setDescription(request.description());
        activity.setScheduledTime(request.scheduledTime());
        activity.setLatitude(request.latitude());
        activity.setLongitude(request.longitude());
        activityRepository.save(activity);

        return ActivityResponse.from(activity, caller);
    }

    /** A group's calendar, sorted chronologically (ascending {@code scheduledTime}). */
    public List<ActivityResponse> listActivities(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        requireCurrentMember(groupId, caller.getId());

        List<Activity> activities = activityRepository.findByGroupIdOrderByScheduledTimeAsc(groupId);
        return activities.stream()
                .map(a -> ActivityResponse.from(a, userRepository.findById(a.getOwnerId()).orElse(null)))
                .toList();
    }

    public ActivityResponse getActivity(String callerEmail, Long groupId, Long activityId) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        requireCurrentMember(groupId, caller.getId());

        Activity activity = findActivityOr404(groupId, activityId);
        return ActivityResponse.from(activity, userRepository.findById(activity.getOwnerId()).orElse(null));
    }

    @Transactional
    public ActivityResponse updateActivity(String callerEmail, Long groupId, Long activityId, UpdateActivityRequest request) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        Activity activity = findActivityOr404(groupId, activityId);
        requireOwnerOrAdmin(groupId, caller.getId(), activity);

        activity.setName(request.name());
        activity.setDescription(request.description());
        activity.setScheduledTime(request.scheduledTime());
        activity.setLatitude(request.latitude());
        activity.setLongitude(request.longitude());
        activityRepository.save(activity);

        return ActivityResponse.from(activity, userRepository.findById(activity.getOwnerId()).orElse(null));
    }

    @Transactional
    public void deleteActivity(String callerEmail, Long groupId, Long activityId) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        Activity activity = findActivityOr404(groupId, activityId);
        requireOwnerOrAdmin(groupId, caller.getId(), activity);

        activityRepository.delete(activity);
    }

    // ---- helpers ----

    /**
     * The activity's owner can edit/delete it; any current admin of the
     * activity's group can also edit or delete it, including activities
     * they don't own (spec.md Activities: "admin edit rights apply to every
     * activity in their group, not just ones they created"). A non-owner,
     * non-admin member is rejected with 403.
     */
    private void requireOwnerOrAdmin(Long groupId, Long callerUserId, Activity activity) {
        if (activity.getOwnerId().equals(callerUserId)) {
            return;
        }

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, callerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));
        if (!membership.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the activity's owner or a group admin can do this");
        }
    }

    private void requireGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
    }

    private void requireCurrentMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    private Activity findActivityOr404(Long groupId, Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
        if (!activity.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found");
        }
        return activity;
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
