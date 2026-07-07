package com.imin.backend.activity;

import com.imin.backend.activity.dto.ActivityResponse;
import com.imin.backend.activity.dto.CreateActivityRequest;
import com.imin.backend.activity.dto.UpdateActivityRequest;
import com.imin.backend.group.GroupBanRepository;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.group.GroupService;
import com.imin.backend.group.dto.CreateGroupRequest;
import com.imin.backend.group.dto.GroupResponse;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Implementer sanity checks for {@link ActivityService} — not a substitute
 * for the tester stage's full acceptance-criteria suite. Exercises the
 * owner-or-admin edit/delete authorization, the membership-gated
 * create/view authorization (reusing Slice 2's {@code GroupService} directly
 * to set up membership/ban/admin state, the same way
 * {@code GroupChatServiceTest} does), chronological calendar ordering, the
 * optional-location shape, and the group-deletion cascade.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ActivityServiceTest {

    @Autowired
    private ActivityService activityService;
    @Autowired
    private ActivityRepository activityRepository;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMembershipRepository membershipRepository;
    @Autowired
    private GroupBanRepository banRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        activityRepository.deleteAll();
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser("alice@example.com", "Alice");
        bob = createUser("bob@example.com", "Bob");
        carol = createUser("carol@example.com", "Carol");
    }

    private User createUser(String email, String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private GroupResponse createGroup(User creator, String name) {
        return groupService.createGroup(creator.getEmail(), new CreateGroupRequest(name, "desc", 1.0, 1.0, List.of()));
    }

    @Test
    void currentMemberCanCreateActivityAndBecomesOwner() {
        GroupResponse group = createGroup(alice, "Hikers");
        groupService.joinGroup(bob.getEmail(), group.id());

        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Trail run", "Easy 5k", Instant.now().plus(1, ChronoUnit.DAYS), 51.5, -0.1));

        assertThat(activity.ownerId()).isEqualTo(bob.getId());
        assertThat(activity.name()).isEqualTo("Trail run");
        assertThat(activity.latitude()).isEqualTo(51.5);
        assertThat(activity.longitude()).isEqualTo(-0.1);
    }

    @Test
    void activityCanBeCreatedWithoutLocation() {
        GroupResponse group = createGroup(alice, "No Location Group");

        ActivityResponse activity = activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("Picnic", null, Instant.now().plus(2, ChronoUnit.DAYS), null, null));

        assertThat(activity.latitude()).isNull();
        assertThat(activity.longitude()).isNull();
    }

    @Test
    void nonMemberCannotCreateOrViewActivities() {
        GroupResponse group = createGroup(alice, "Private Group");

        assertThatThrownBy(() -> activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Sneaky", null, Instant.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        assertThatThrownBy(() -> activityService.listActivities(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void listActivitiesReturnsCalendarSortedChronologicallyNotByInsertionOrder() {
        GroupResponse group = createGroup(alice, "Calendar Group");
        Instant now = Instant.now();

        // Insert out of chronological order to confirm sorting isn't insertion-order.
        activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("Third", null, now.plus(3, ChronoUnit.DAYS), null, null));
        activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("First", null, now.plus(1, ChronoUnit.DAYS), null, null));
        activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("Second", null, now.plus(2, ChronoUnit.DAYS), null, null));

        List<ActivityResponse> activities = activityService.listActivities(alice.getEmail(), group.id());

        assertThat(activities).extracting(ActivityResponse::name).containsExactly("First", "Second", "Third");
    }

    @Test
    void ownerCanEditOwnActivity() {
        GroupResponse group = createGroup(alice, "Edit Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Original", "desc", Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        ActivityResponse updated = activityService.updateActivity(bob.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Updated", "new desc", Instant.now().plus(2, ChronoUnit.DAYS), 10.0, 20.0));

        assertThat(updated.name()).isEqualTo("Updated");
        assertThat(updated.description()).isEqualTo("new desc");
        assertThat(updated.latitude()).isEqualTo(10.0);
        assertThat(updated.longitude()).isEqualTo(20.0);
    }

    @Test
    void adminCanEditAndDeleteActivityTheyDoNotOwn() {
        GroupResponse group = createGroup(alice, "Admin Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Bob's Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        // alice is the group's admin (creator), not the activity's owner.
        ActivityResponse updated = activityService.updateActivity(alice.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Admin Edited", null, Instant.now().plus(3, ChronoUnit.DAYS), null, null));
        assertThat(updated.name()).isEqualTo("Admin Edited");

        activityService.deleteActivity(alice.getEmail(), group.id(), activity.id());
        assertThat(activityRepository.findById(activity.id())).isEmpty();
    }

    @Test
    void nonOwnerNonAdminMemberCannotEditOrDeleteActivity() {
        GroupResponse group = createGroup(alice, "Three Member Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.joinGroup(carol.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Bob's Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        assertThatThrownBy(() -> activityService.updateActivity(carol.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Hijacked", null, Instant.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        assertThatThrownBy(() -> activityService.deleteActivity(carol.getEmail(), group.id(), activity.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void trueNonMemberCannotEditOrDeleteActivityTheyDoNotOwn() {
        // Carol has never joined this group at all (never a member, never
        // banned) -- distinct from nonOwnerNonAdminMemberCannotEditOrDeleteActivity
        // above, which covers a *current member* who isn't the owner/admin.
        // Closes the reviewer-flagged nit: the true non-member case wasn't
        // separately tested before.
        GroupResponse group = createGroup(alice, "Outsiders Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Bob's Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        assertThat(membershipRepository.findByGroupIdAndUserId(group.id(), carol.getId())).isEmpty();

        assertThatThrownBy(() -> activityService.updateActivity(carol.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Hijacked", null, Instant.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        assertThatThrownBy(() -> activityService.deleteActivity(carol.getEmail(), group.id(), activity.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        // Confirm the activity was untouched by the rejected attempts.
        assertThat(activityRepository.findById(activity.id())).isPresent();
        assertThat(activityRepository.findById(activity.id()).orElseThrow().getName()).isEqualTo("Bob's Activity");
    }

    @Test
    void editedActivityChangesArePersistedAndVisibleOnSubsequentGet() {
        // Confirms the PATCH's effect is actually durable -- not just that
        // the update call's own return value looked right, but that a
        // separate, later read sees the same persisted change.
        GroupResponse group = createGroup(alice, "Persistence Group");
        ActivityResponse activity = activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("Original", "orig desc", Instant.now().plus(1, ChronoUnit.DAYS), 1.0, 2.0));

        Instant newTime = Instant.now().plus(5, ChronoUnit.DAYS);
        activityService.updateActivity(alice.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Renamed", "updated desc", newTime, 9.0, 8.0));

        ActivityResponse fetched = activityService.getActivity(alice.getEmail(), group.id(), activity.id());
        assertThat(fetched.name()).isEqualTo("Renamed");
        assertThat(fetched.description()).isEqualTo("updated desc");
        assertThat(fetched.scheduledTime()).isEqualTo(newTime);
        assertThat(fetched.latitude()).isEqualTo(9.0);
        assertThat(fetched.longitude()).isEqualTo(8.0);

        List<ActivityResponse> listed = activityService.listActivities(alice.getEmail(), group.id());
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).name()).isEqualTo("Renamed");
    }

    @Test
    void bannedMemberLosesActivityAccessImmediatelyAndRegainsItOnUnban() {
        GroupResponse group = createGroup(alice, "Banhammer Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(bob.getEmail(), group.id(),
                new CreateActivityRequest("Pre-ban Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        assertThatThrownBy(() -> activityService.listActivities(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        groupService.unbanMember(alice.getEmail(), group.id(), bob.getId());
        groupService.joinGroup(bob.getEmail(), group.id());

        List<ActivityResponse> activities = activityService.listActivities(bob.getEmail(), group.id());
        assertThat(activities).extracting(ActivityResponse::id).contains(activity.id());
    }

    @Test
    void deletingGroupCascadesToItsActivities() {
        GroupResponse group = createGroup(alice, "Solo Group");
        activityService.createActivity(alice.getEmail(), group.id(),
                new CreateActivityRequest("Doomed Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));
        assertThat(activityRepository.findByGroupIdOrderByScheduledTimeAsc(group.id())).isNotEmpty();

        groupService.leaveGroup(alice.getEmail(), group.id());

        assertThat(groupRepository.findById(group.id())).isEmpty();
        assertThat(activityRepository.findByGroupIdOrderByScheduledTimeAsc(group.id())).isEmpty();
    }

    @Test
    void activityNotFoundReturns404() {
        GroupResponse group = createGroup(alice, "Empty Group");

        assertThatThrownBy(() -> activityService.getActivity(alice.getEmail(), group.id(), 999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void groupNotFoundReturns404() {
        assertThatThrownBy(() -> activityService.listActivities(alice.getEmail(), 999_999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    // ---- try-demo-account restriction enforcement (specs/try-demo-account/spec.md) ----

    @Test
    void demoAccountCannotUpdateOrDeleteAnActivityItOwns() {
        User demo = createUser("demo@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        GroupResponse group = createGroup(alice, "Demo Activity Group");
        groupService.joinGroup(demo.getEmail(), group.id());
        ActivityResponse activity = activityService.createActivity(demo.getEmail(), group.id(),
                new CreateActivityRequest("Demo's Activity", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        assertThatThrownBy(() -> activityService.updateActivity(demo.getEmail(), group.id(), activity.id(),
                new UpdateActivityRequest("Hijacked", null, Instant.now(), null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        assertThatThrownBy(() -> activityService.deleteActivity(demo.getEmail(), group.id(), activity.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        // Untouched -- the demo-owned activity survives both rejected attempts.
        Activity persisted = activityRepository.findById(activity.id()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Demo's Activity");
    }

    @Test
    void demoAccountCanStillCreateAndListActivitiesNormally() {
        // Spot-check of the explicitly-allowed surface (createActivity, listActivities).
        User demo = createUser("demo@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        GroupResponse group = createGroup(alice, "Demo Allowed Activity Group");
        groupService.joinGroup(demo.getEmail(), group.id());

        ActivityResponse created = activityService.createActivity(demo.getEmail(), group.id(),
                new CreateActivityRequest("Demo Picnic", null, Instant.now().plus(1, ChronoUnit.DAYS), null, null));

        assertThat(created.ownerId()).isEqualTo(demo.getId());
        assertThat(activityService.listActivities(demo.getEmail(), group.id()))
                .extracting(ActivityResponse::name).contains("Demo Picnic");
    }
}
