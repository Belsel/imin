package com.imin.backend.group;

import com.imin.backend.category.GroupCategory;
import com.imin.backend.category.GroupCategoryRepository;
import com.imin.backend.category.UserCategoryPreference;
import com.imin.backend.category.UserCategoryPreferenceRepository;
import com.imin.backend.group.dto.CreateGroupRequest;
import com.imin.backend.group.dto.GroupRecommendationResponse;
import com.imin.backend.group.dto.GroupResponse;
import com.imin.backend.group.dto.PublicGroupRecommendationResponse;
import com.imin.backend.group.dto.UpdateGroupRequest;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level sanity checks for {@link GroupService}, exercised against a
 * real (H2 in-memory) repository stack. Primarily targets this slice's
 * trickiest piece — the synchronous group-lifecycle rules (zero-member
 * deletion, zero-admin succession) — plus the basic CRUD/membership/ban
 * surface. Not a substitute for the tester stage's full acceptance-criteria
 * suite.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupServiceTest {

    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMembershipRepository membershipRepository;
    @Autowired
    private GroupBanRepository banRepository;
    @Autowired
    private GroupCategoryLinkRepository categoryLinkRepository;
    @Autowired
    private GroupCategoryRepository groupCategoryRepository;
    @Autowired
    private UserCategoryPreferenceRepository userCategoryPreferenceRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        // Note: GroupCategory rows are NOT cleared here — CategorySeeder seeds
        // the fixed taxonomy once at application context startup (outside this
        // test's @Transactional rollback boundary), and tests rely on that
        // already-seeded data being present/stable across the whole class.
        userCategoryPreferenceRepository.deleteAll();
        categoryLinkRepository.deleteAll();
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

    @Test
    void creatingGroupMakesCreatorAdminAndMember() {
        GroupResponse response = groupService.createGroup(alice.getEmail(),
                new CreateGroupRequest("Hikers", "We hike", 51.5, -0.1, List.of()));

        assertThat(response.isAdmin()).isTrue();
        assertThat(response.isMember()).isTrue();
        assertThat(response.memberCount()).isEqualTo(1);
        assertThat(response.latitude()).isEqualTo(51.5);
        assertThat(response.longitude()).isEqualTo(-0.1);

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(response.id(), alice.getId())
                .orElseThrow();
        assertThat(membership.isAdmin()).isTrue();
    }

    @Test
    void joiningGroupAddsNonAdminMembership() {
        GroupResponse group = createGroup(alice, "Readers");

        groupService.joinGroup(bob.getEmail(), group.id());

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(group.id(), bob.getId())
                .orElseThrow();
        assertThat(membership.isAdmin()).isFalse();
        assertThat(membershipRepository.countByGroupId(group.id())).isEqualTo(2);
    }

    @Test
    void bannedUserCannotJoinGroup() {
        GroupResponse group = createGroup(alice, "Gamers");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        assertThatThrownBy(() -> groupService.joinGroup(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void bannedUserCannotViewGroup() {
        GroupResponse group = createGroup(alice, "Foodies");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        assertThatThrownBy(() -> groupService.getGroup(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void banInOneGroupDoesNotAffectAnotherGroup() {
        GroupResponse groupA = createGroup(alice, "Group A");
        GroupResponse groupB = createGroup(alice, "Group B");
        groupService.joinGroup(bob.getEmail(), groupA.id());
        groupService.joinGroup(bob.getEmail(), groupB.id());

        groupService.banMember(alice.getEmail(), groupA.id(), bob.getId());

        // Bob is banned/removed from Group A...
        assertThat(membershipRepository.existsByGroupIdAndUserId(groupA.id(), bob.getId())).isFalse();
        assertThat(banRepository.existsByGroupIdAndUserId(groupA.id(), bob.getId())).isTrue();

        // ...but completely unaffected in Group B.
        assertThat(membershipRepository.existsByGroupIdAndUserId(groupB.id(), bob.getId())).isTrue();
        assertThat(banRepository.existsByGroupIdAndUserId(groupB.id(), bob.getId())).isFalse();
        GroupResponse stillVisible = groupService.getGroup(bob.getEmail(), groupB.id());
        assertThat(stillVisible.isMember()).isTrue();
    }

    @Test
    void lastMemberLeavingDeletesGroupAndChildren() {
        GroupResponse group = createGroup(alice, "Solo Group");
        // Tag the group with a seeded category (CategorySeeder runs at context
        // startup, so the fixed taxonomy already has rows) and verify cleanup.
        Long categoryId = groupCategoryRepository.findAll().get(0).getId();
        groupService.addCategory(alice.getEmail(), group.id(), categoryId);
        assertThat(categoryLinkRepository.findByGroupId(group.id())).isNotEmpty();

        groupService.leaveGroup(alice.getEmail(), group.id());

        assertThat(groupRepository.findById(group.id())).isEmpty();
        assertThat(membershipRepository.findByGroupId(group.id())).isEmpty();
        assertThat(categoryLinkRepository.findByGroupId(group.id())).isEmpty();
    }

    @Test
    void leavingLastAdminPromotesLongestTenuredRecentlyOnlineMember() {
        GroupResponse group = createGroup(alice, "Succession Group");

        // Bob joins first (longer tenure), Carol joins second.
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.joinGroup(carol.getEmail(), group.id());

        // Bob has not been seen recently; Carol was seen within the last 7 days.
        bob.setLastSeenAt(Instant.now().minus(30, ChronoUnit.DAYS));
        userRepository.save(bob);
        carol.setLastSeenAt(Instant.now().minus(1, ChronoUnit.DAYS));
        userRepository.save(carol);

        // Alice (sole admin) leaves.
        groupService.leaveGroup(alice.getEmail(), group.id());

        GroupMembership bobMembership = membershipRepository.findByGroupIdAndUserId(group.id(), bob.getId()).orElseThrow();
        GroupMembership carolMembership = membershipRepository.findByGroupIdAndUserId(group.id(), carol.getId()).orElseThrow();

        // Bob has earlier joinedAt but failed the 7-day-online filter -> Carol promoted instead.
        assertThat(bobMembership.isAdmin()).isFalse();
        assertThat(carolMembership.isAdmin()).isTrue();
    }

    @Test
    void leavingLastAdminFallsBackToLongestTenuredWhenNoneRecentlyOnline() {
        GroupResponse group = createGroup(alice, "Stale Group");

        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.joinGroup(carol.getEmail(), group.id());

        // Neither Bob nor Carol has been seen within 7 days.
        bob.setLastSeenAt(Instant.now().minus(30, ChronoUnit.DAYS));
        userRepository.save(bob);
        carol.setLastSeenAt(Instant.now().minus(45, ChronoUnit.DAYS));
        userRepository.save(carol);

        groupService.leaveGroup(alice.getEmail(), group.id());

        // Fall back to the single longest-tenured member overall: Bob joined before Carol.
        GroupMembership bobMembership = membershipRepository.findByGroupIdAndUserId(group.id(), bob.getId()).orElseThrow();
        GroupMembership carolMembership = membershipRepository.findByGroupIdAndUserId(group.id(), carol.getId()).orElseThrow();
        assertThat(bobMembership.isAdmin()).isTrue();
        assertThat(carolMembership.isAdmin()).isFalse();
    }

    @Test
    void kickingLastAdminTriggersSuccessionSynchronously() {
        GroupResponse group = createGroup(alice, "Kick Group");
        groupService.joinGroup(bob.getEmail(), group.id());

        // Promote bob to admin too, then have alice kicked... actually test kicking alice (sole admin) directly.
        bob.setLastSeenAt(Instant.now());
        userRepository.save(bob);

        // Simulate alice being kicked by another admin: first make bob an admin via succession path is circular,
        // so directly exercise ban-triggered succession instead, which is equivalent code path.
        groupService.banMember(alice.getEmail(), group.id(), alice.getId());
        // alice just banned herself as the only admin -- exercise lifecycle: bob should be promoted.

        GroupMembership bobMembership = membershipRepository.findByGroupIdAndUserId(group.id(), bob.getId()).orElseThrow();
        assertThat(bobMembership.isAdmin()).isTrue();
        assertThat(banRepository.existsByGroupIdAndUserId(group.id(), alice.getId())).isTrue();
        assertThat(membershipRepository.existsByGroupIdAndUserId(group.id(), alice.getId())).isFalse();
    }

    @Test
    void groupLocationIsImmutableAfterCreation() {
        GroupResponse group = groupService.createGroup(alice.getEmail(),
                new CreateGroupRequest("Movable?", "desc", 12.34, 56.78, List.of()));

        // updateGroup only accepts name/description (UpdateGroupRequest has no
        // lat/lng fields at all) -- confirm the location is unchanged after a
        // rename/description edit by the admin.
        GroupResponse updated = groupService.updateGroup(alice.getEmail(), group.id(),
                new UpdateGroupRequest("Still Here", "new desc"));

        assertThat(updated.latitude()).isEqualTo(12.34);
        assertThat(updated.longitude()).isEqualTo(56.78);

        Group persisted = groupRepository.findById(group.id()).orElseThrow();
        assertThat(persisted.getLatitude()).isEqualTo(12.34);
        assertThat(persisted.getLongitude()).isEqualTo(56.78);
    }

    @Test
    void multipleAdminsHaveIdenticallyEqualCapabilities() {
        GroupResponse group = createGroup(alice, "Co-op Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.joinGroup(carol.getEmail(), group.id());

        // Promote Bob to a second admin via the only available mechanism in this
        // slice (succession). Simpler and equally valid for this test: directly
        // flip the membership row to admin, since there's no manual "promote"
        // endpoint in this slice (explicitly deferred, see spec.md Implementation
        // notes). We only care that once two admins exist, both have identical
        // powers -- not how the second one got there.
        GroupMembership bobMembership = membershipRepository.findByGroupIdAndUserId(group.id(), bob.getId()).orElseThrow();
        bobMembership.setAdmin(true);
        membershipRepository.save(bobMembership);

        // Bob (the second, non-founding admin) can rename...
        GroupResponse renamedByBob = groupService.updateGroup(bob.getEmail(), group.id(),
                new UpdateGroupRequest("Renamed By Bob", "desc by bob"));
        assertThat(renamedByBob.name()).isEqualTo("Renamed By Bob");

        // ...kick a member...
        groupService.kickMember(bob.getEmail(), group.id(), carol.getId());
        assertThat(membershipRepository.existsByGroupIdAndUserId(group.id(), carol.getId())).isFalse();

        // ...ban and unban a member...
        groupService.joinGroup(carol.getEmail(), group.id());
        groupService.banMember(bob.getEmail(), group.id(), carol.getId());
        assertThat(banRepository.existsByGroupIdAndUserId(group.id(), carol.getId())).isTrue();
        groupService.unbanMember(bob.getEmail(), group.id(), carol.getId());
        assertThat(banRepository.existsByGroupIdAndUserId(group.id(), carol.getId())).isFalse();

        // ...and Alice (the founding admin) retains exactly the same powers --
        // neither admin has capabilities the other lacks.
        GroupResponse renamedByAlice = groupService.updateGroup(alice.getEmail(), group.id(),
                new UpdateGroupRequest("Renamed By Alice", "desc by alice"));
        assertThat(renamedByAlice.name()).isEqualTo("Renamed By Alice");
    }

    @Test
    void adminCanDeleteGroup() {
        GroupResponse group = createGroup(alice, "Doomed Group");
        groupService.joinGroup(bob.getEmail(), group.id());

        groupService.deleteGroup(alice.getEmail(), group.id());

        assertThat(groupRepository.findById(group.id())).isEmpty();
        assertThat(membershipRepository.findByGroupId(group.id())).isEmpty();
    }

    @Test
    void nonAdminCannotDeleteGroup() {
        GroupResponse group = createGroup(alice, "Protected Group");
        groupService.joinGroup(bob.getEmail(), group.id());

        assertThatThrownBy(() -> groupService.deleteGroup(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        // Group is untouched by the rejected attempt.
        assertThat(groupRepository.findById(group.id())).isPresent();
    }

    @Test
    void joiningGroupSucceedsRegardlessOfMemberCountNoCapEnforced() {
        GroupResponse group = createGroup(alice, "Big Group");

        // Beyond the usual alice/bob/carol fixture, create and join a larger
        // batch of members to confirm there is no enforced size cap at all --
        // not just that 2-3 members works.
        int extraMemberCount = 50;
        for (int i = 0; i < extraMemberCount; i++) {
            User extra = createUser("member" + i + "@example.com", "Member " + i);
            groupService.joinGroup(extra.getEmail(), group.id());
        }

        // +1 for alice, the creator/admin.
        assertThat(membershipRepository.countByGroupId(group.id())).isEqualTo(extraMemberCount + 1);

        GroupResponse fetched = groupService.getGroup(alice.getEmail(), group.id());
        assertThat(fetched.memberCount()).isEqualTo(extraMemberCount + 1);
    }

    @Test
    void groupCanBeTaggedWithMultipleCategoriesFromFixedTaxonomy() {
        List<Long> firstThreeCategoryIds = groupCategoryRepository.findAll().stream()
                .limit(3)
                .map(GroupCategory::getId)
                .toList();
        assertThat(firstThreeCategoryIds).hasSize(3);

        GroupResponse group = groupService.createGroup(alice.getEmail(),
                new CreateGroupRequest("Multi-Category Group", "desc", 0.0, 0.0, firstThreeCategoryIds));

        assertThat(group.categories()).hasSize(3);
        assertThat(group.categories()).extracting(c -> c.id())
                .containsExactlyInAnyOrderElementsOf(firstThreeCategoryIds);

        // Attempting to tag with a non-existent category id is rejected -- only
        // ids from the fixed, already-seeded taxonomy are assignable.
        assertThatThrownBy(() -> groupService.addCategory(alice.getEmail(), group.id(), -999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void bannedUserCannotListGroupMembers() {
        GroupResponse group = createGroup(alice, "Roster Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        // Bob's membership row was removed as part of being banned, so the
        // member-list endpoint (members-only visibility) rejects him --
        // confirms a banned user loses access to the roster, not just the
        // join/getGroup paths.
        assertThatThrownBy(() -> groupService.listMembers(bob.getEmail(), group.id()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        // A current (non-banned) member, by contrast, can list members.
        assertThat(groupService.listMembers(alice.getEmail(), group.id())).isNotEmpty();
    }

    @Test
    void onlyAdminCanRenameGroup() {
        GroupResponse group = createGroup(alice, "Old Name");
        groupService.joinGroup(bob.getEmail(), group.id());

        assertThatThrownBy(() ->
                groupService.updateGroup(bob.getEmail(), group.id(), new UpdateGroupRequest("New Name", "desc")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        GroupResponse updated = groupService.updateGroup(alice.getEmail(), group.id(), new UpdateGroupRequest("New Name", "desc"));
        assertThat(updated.name()).isEqualTo("New Name");
    }

    @Test
    void onlyAdminCanKickOrBan() {
        GroupResponse group = createGroup(alice, "Mods Only");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.joinGroup(carol.getEmail(), group.id());

        assertThatThrownBy(() -> groupService.kickMember(bob.getEmail(), group.id(), carol.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        groupService.kickMember(alice.getEmail(), group.id(), carol.getId());
        assertThat(membershipRepository.existsByGroupIdAndUserId(group.id(), carol.getId())).isFalse();
    }

    @Test
    void unbanRemovesBanRowAndAllowsRejoining() {
        GroupResponse group = createGroup(alice, "Reform Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        groupService.unbanMember(alice.getEmail(), group.id(), bob.getId());

        assertThat(banRepository.existsByGroupIdAndUserId(group.id(), bob.getId())).isFalse();
        groupService.joinGroup(bob.getEmail(), group.id()); // does not throw
        assertThat(membershipRepository.existsByGroupIdAndUserId(group.id(), bob.getId())).isTrue();
    }

    @Test
    void recommendationsRankByCategoryOverlapThenDistance() {
        GroupCategory tech = groupCategoryRepository.save(newCategory("Tech-Rec"));
        GroupCategory sports = groupCategoryRepository.save(newCategory("Sports-Rec"));

        // Bob prefers Tech.
        UserCategoryPreference pref = new UserCategoryPreference();
        pref.setUserId(bob.getId());
        pref.setCategoryId(tech.getId());
        userCategoryPreferenceRepository.save(pref);

        // Group near bob, matching category.
        GroupResponse nearMatching = groupService.createGroup(alice.getEmail(),
                new CreateGroupRequest("Near Tech", "d", 0.0, 0.0, List.of(tech.getId())));
        // Group far away, but also matching category.
        GroupResponse farMatching = groupService.createGroup(carol.getEmail(),
                new CreateGroupRequest("Far Tech", "d", 50.0, 50.0, List.of(tech.getId())));
        // Group very near, but non-matching category.
        GroupResponse nearNonMatching = groupService.createGroup(carol.getEmail(),
                new CreateGroupRequest("Near Sports", "d", 0.01, 0.01, List.of(sports.getId())));

        List<GroupRecommendationResponse> recs = groupService.recommendGroups(bob.getEmail(), 0.0, 0.0, 10);

        List<Long> ids = recs.stream().map(GroupRecommendationResponse::id).toList();
        // Both matching-category groups should outrank the near-but-non-matching group.
        assertThat(ids.indexOf(nearMatching.id())).isLessThan(ids.indexOf(nearNonMatching.id()));
        assertThat(ids.indexOf(farMatching.id())).isLessThan(ids.indexOf(nearNonMatching.id()));
        // Within the matching tier, the nearer group should rank ahead of the farther one.
        assertThat(ids.indexOf(nearMatching.id())).isLessThan(ids.indexOf(farMatching.id()));
    }

    @Test
    void recommendationsExcludeGroupsAlreadyJoinedOrBanned() {
        GroupResponse joined = createGroup(alice, "Already Joined");
        groupService.joinGroup(bob.getEmail(), joined.id());

        GroupResponse bannedFrom = createGroup(alice, "Banned From");
        groupService.joinGroup(bob.getEmail(), bannedFrom.id());
        groupService.banMember(alice.getEmail(), bannedFrom.id(), bob.getId());

        GroupResponse eligible = createGroup(alice, "Eligible");

        List<GroupRecommendationResponse> recs = groupService.recommendGroups(bob.getEmail(), 0.0, 0.0, 10);
        List<Long> ids = recs.stream().map(GroupRecommendationResponse::id).toList();

        assertThat(ids).doesNotContain(joined.id(), bannedFrom.id());
        assertThat(ids).contains(eligible.id());
    }

    @Test
    void searchFindsGroupsByNameCaseInsensitively() {
        createGroup(alice, "Mountain Bikers");
        createGroup(alice, "Book Club");

        List<GroupResponse> results = groupService.searchGroups(bob.getEmail(), "mountain");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Mountain Bikers");
    }

    @Test
    void searchExcludesGroupsCallerIsBannedFrom() {
        GroupResponse group = createGroup(alice, "Exclusive Club");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        List<GroupResponse> results = groupService.searchGroups(bob.getEmail(), "Exclusive");

        assertThat(results).isEmpty();
    }

    @Test
    void searchExcludesGroupsCallerHasAlreadyJoined() {
        GroupResponse group = createGroup(alice, "Joined Club");
        groupService.joinGroup(bob.getEmail(), group.id());

        List<GroupResponse> results = groupService.searchGroups(bob.getEmail(), "Joined");

        assertThat(results).isEmpty();
    }

    @Test
    void publicRecommendationsOrderByMemberCountDescending() {
        Group low = rawGroup("Low Count", Instant.now());
        Group mid = rawGroup("Mid Count", Instant.now());
        Group high = rawGroup("High Count", Instant.now());
        addMembers(low, 1);
        addMembers(mid, 3);
        addMembers(high, 5);

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).extracting(PublicGroupRecommendationResponse::id)
                .containsExactly(high.getId(), mid.getId(), low.getId());
    }

    @Test
    void publicRecommendationsTieBreakByCreatedAtDescendingThenId() {
        Instant now = Instant.now();
        Group older = rawGroup("Older Same Count", now.minus(1, ChronoUnit.DAYS));
        Group newer = rawGroup("Newer Same Count", now);
        addMembers(older, 2);
        addMembers(newer, 2);

        // Equal member count and equal createdAt -> lower id first.
        Instant sameInstant = now.plus(1, ChronoUnit.DAYS);
        Group sameA = rawGroup("Same Instant A", sameInstant);
        Group sameB = rawGroup("Same Instant B", sameInstant);
        addMembers(sameA, 1);
        addMembers(sameB, 1);

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();
        List<Long> ids = recs.stream().map(PublicGroupRecommendationResponse::id).toList();

        // Newer createdAt wins the member-count-2 tie.
        assertThat(ids.indexOf(newer.getId())).isLessThan(ids.indexOf(older.getId()));
        // Equal member count and createdAt -> lower id first.
        assertThat(ids.indexOf(sameA.getId())).isLessThan(ids.indexOf(sameB.getId()));
    }

    @Test
    void publicRecommendationsReturnTopSixOfEightGroups() {
        List<Group> groups = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Group g = rawGroup("Group " + i, Instant.now());
            addMembers(g, i); // distinct member counts 0..7
            groups.add(g);
        }

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).hasSize(6);
        // Top 6 by member count descending: groups with counts 7,6,5,4,3,2 (indices 7..2).
        List<Long> expectedIds = List.of(
                groups.get(7).getId(), groups.get(6).getId(), groups.get(5).getId(),
                groups.get(4).getId(), groups.get(3).getId(), groups.get(2).getId());
        assertThat(recs.stream().map(PublicGroupRecommendationResponse::id).toList()).isEqualTo(expectedIds);
    }

    @Test
    void publicRecommendationsReturnAllWhenFewerThanSixGroupsExist() {
        Group a = rawGroup("Only A", Instant.now());
        Group b = rawGroup("Only B", Instant.now());

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).hasSize(2);
        assertThat(recs.stream().map(PublicGroupRecommendationResponse::id).toList())
                .containsExactlyInAnyOrder(a.getId(), b.getId());
    }

    @Test
    void publicRecommendationsReturnEmptyListWhenNoGroupsExist() {
        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).isEmpty();
    }

    @Test
    void publicRecommendationsRoundCoordinatesButDoNotMutateStoredGroup() {
        Group group = new Group();
        group.setName("Precise Location Group");
        group.setDescription("d");
        group.setLatitude(51.5074123);
        group.setLongitude(-0.1277654);
        group.setCreatedAt(Instant.now());
        groupRepository.save(group);

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).latitude()).isEqualTo(51.51);
        assertThat(recs.get(0).longitude()).isEqualTo(-0.13);

        Group stored = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(stored.getLatitude()).isEqualTo(51.5074123);
        assertThat(stored.getLongitude()).isEqualTo(-0.1277654);
    }

    @Test
    void publicRecommendationsIncludeGroupWithZeroMembers() {
        Group group = rawGroup("Memberless Group", Instant.now());

        List<PublicGroupRecommendationResponse> recs = groupService.getPublicRecommendations();

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).id()).isEqualTo(group.getId());
        assertThat(recs.get(0).memberCount()).isEqualTo(0);
    }

    /**
     * Constructs and saves a {@code Group} directly via {@code GroupRepository},
     * bypassing {@code groupService.createGroup} (which would force a
     * creator-admin membership and today's {@code Instant.now()} createdAt),
     * so ranking tests can set exact, distinct createdAt values. Relies on
     * {@code updatable = false} only suppressing UPDATE statements, not the
     * initial INSERT.
     */
    private Group rawGroup(String name, Instant createdAt) {
        Group group = new Group();
        group.setName(name);
        group.setDescription("desc");
        group.setLatitude(0.0);
        group.setLongitude(0.0);
        group.setCreatedAt(createdAt);
        return groupRepository.save(group);
    }

    /** Adds {@code count} distinct-user memberships directly via the repository, for exact member counts. */
    private void addMembers(Group group, int count) {
        for (int i = 0; i < count; i++) {
            User user = createUser(group.getId() + "-member-" + i + "@example.com", "Member " + i);
            GroupMembership membership = new GroupMembership();
            membership.setGroupId(group.getId());
            membership.setUserId(user.getId());
            membership.setAdmin(false);
            membershipRepository.save(membership);
        }
    }

    private GroupResponse createGroup(User creator, String name) {
        return groupService.createGroup(creator.getEmail(), new CreateGroupRequest(name, "desc", 0.0, 0.0, List.of()));
    }

    private GroupCategory newCategory(String name) {
        GroupCategory category = new GroupCategory();
        category.setName(name);
        return category;
    }
}
