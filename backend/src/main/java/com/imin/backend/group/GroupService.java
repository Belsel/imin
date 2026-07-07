package com.imin.backend.group;

import com.imin.backend.activity.ActivityRepository;
import com.imin.backend.category.GroupCategory;
import com.imin.backend.category.GroupCategoryRepository;
import com.imin.backend.category.UserCategoryPreference;
import com.imin.backend.category.UserCategoryPreferenceRepository;
import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.chat.GroupChatMessageRepository;
import com.imin.backend.group.dto.CreateGroupRequest;
import com.imin.backend.group.dto.GroupBanResponse;
import com.imin.backend.group.dto.GroupMemberResponse;
import com.imin.backend.group.dto.GroupRecommendationResponse;
import com.imin.backend.group.dto.GroupResponse;
import com.imin.backend.group.dto.PublicGroupRecommendationResponse;
import com.imin.backend.group.dto.UpdateGroupRequest;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns group CRUD, search, recommendations, membership mutations, and admin
 * actions (kick/ban/unban/rename/delete) — including the synchronous
 * group-lifecycle rules (zero-member deletion, zero-admin succession) that
 * must run in the same transaction as every membership removal. See
 * design.md §6b for the rationale (no {@code @Scheduled} sweep, since
 * Render's free tier sleeps on idle).
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int ONLINE_WITHIN_DAYS = 7;

    /** Half of Earth's circumference (km) — the maximum possible distance between any two points on Earth. */
    private static final double EARTH_MAX_DISTANCE_KM = 20038.0;

    private static final int PUBLIC_RECOMMENDATION_LIMIT = 6;

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupBanRepository banRepository;
    private final GroupCategoryLinkRepository categoryLinkRepository;
    private final GroupCategoryRepository groupCategoryRepository;
    private final UserCategoryPreferenceRepository userCategoryPreferenceRepository;
    private final UserRepository userRepository;
    private final GroupChatMessageRepository groupChatMessageRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public GroupResponse createGroup(String creatorEmail, CreateGroupRequest request) {
        User creator = findUserByEmail(creatorEmail);

        Group group = new Group();
        group.setName(request.name());
        group.setDescription(request.description());
        group.setLatitude(request.latitude());
        group.setLongitude(request.longitude());
        groupRepository.save(group);

        List<Long> categoryIds = request.categoryIds() == null ? List.of() : request.categoryIds().stream().distinct().toList();
        if (!categoryIds.isEmpty()) {
            List<GroupCategory> categories = groupCategoryRepository.findAllById(categoryIds);
            if (categories.size() != categoryIds.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more category ids do not exist");
            }
            for (Long categoryId : categoryIds) {
                GroupCategoryLink link = new GroupCategoryLink();
                link.setGroupId(group.getId());
                link.setCategoryId(categoryId);
                categoryLinkRepository.save(link);
            }
        }

        GroupMembership membership = new GroupMembership();
        membership.setGroupId(group.getId());
        membership.setUserId(creator.getId());
        membership.setAdmin(true);
        membershipRepository.save(membership);

        return toGroupResponse(group, creator.getId());
    }

    public GroupResponse getGroup(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        Group group = findGroupOr404(groupId);

        if (banRepository.existsByGroupIdAndUserId(groupId, caller.getId())) {
            // Banned users get no visibility — 404 rather than 403 so as not to
            // confirm "you're banned" vs. "doesn't exist" (design.md §4).
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }

        return toGroupResponse(group, caller.getId());
    }

    public List<GroupResponse> searchGroups(String callerEmail, String query) {
        User caller = findUserByEmail(callerEmail);
        List<Group> groups = (query == null || query.isBlank())
                ? groupRepository.findAll()
                : groupRepository.findByNameContainingIgnoreCase(query);

        Set<Long> excluded = bannedGroupIdsForUser(caller.getId());
        Set<Long> memberGroupIds = membershipRepository.findByUserId(caller.getId()).stream()
                .map(GroupMembership::getGroupId)
                .collect(Collectors.toSet());

        return groups.stream()
                .filter(g -> !excluded.contains(g.getId()))
                .filter(g -> !memberGroupIds.contains(g.getId()))
                .map(g -> toGroupResponse(g, caller.getId()))
                .toList();
    }

    public List<GroupResponse> getMyGroups(String callerEmail) {
        User caller = findUserByEmail(callerEmail);
        List<GroupMembership> memberships = membershipRepository.findByUserId(caller.getId());
        return memberships.stream()
                .map(m -> groupRepository.findById(m.getGroupId()))
                .filter(java.util.Optional::isPresent)
                .map(opt -> toGroupResponse(opt.get(), caller.getId()))
                .toList();
    }

    public List<GroupMemberResponse> listMembers(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        requireMember(groupId, caller.getId());

        List<GroupMembership> memberships = membershipRepository.findByGroupIdOrderByJoinedAtAsc(groupId);
        Map<Long, User> usersById = usersById(memberships.stream().map(GroupMembership::getUserId).toList());
        return memberships.stream()
                .map(m -> GroupMemberResponse.from(m, usersById.get(m.getUserId())))
                .toList();
    }

    public List<GroupBanResponse> listBans(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());

        List<GroupBan> bans = banRepository.findByGroupId(groupId);
        Map<Long, User> usersById = usersById(bans.stream().map(GroupBan::getUserId).toList());
        return bans.stream()
                .map(b -> GroupBanResponse.from(b, usersById.get(b.getUserId())))
                .toList();
    }

    @Transactional
    public GroupResponse updateGroup(String callerEmail, Long groupId, UpdateGroupRequest request) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());

        Group group = findGroupOr404(groupId);
        group.setName(request.name());
        group.setDescription(request.description());
        groupRepository.save(group);

        return toGroupResponse(group, caller.getId());
    }

    @Transactional
    public void deleteGroup(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        findGroupOr404(groupId);

        deleteGroupAndChildren(groupId);
    }

    @Transactional
    public GroupResponse joinGroup(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        Group group = findGroupOr404(groupId);

        if (banRepository.existsByGroupIdAndUserId(groupId, caller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are banned from this group");
        }

        if (!membershipRepository.existsByGroupIdAndUserId(groupId, caller.getId())) {
            GroupMembership membership = new GroupMembership();
            membership.setGroupId(groupId);
            membership.setUserId(caller.getId());
            membership.setAdmin(false);
            membershipRepository.save(membership);
        }

        return toGroupResponse(group, caller.getId());
    }

    @Transactional
    public void leaveGroup(String callerEmail, Long groupId) {
        User caller = findUserByEmail(callerEmail);
        findGroupOr404(groupId);

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, caller.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "You are not a member of this group"));

        membershipRepository.delete(membership);
        runLifecycleChecks(groupId);
    }

    @Transactional
    public void kickMember(String callerEmail, Long groupId, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        findGroupOr404(groupId);

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this group"));

        membershipRepository.delete(membership);
        runLifecycleChecks(groupId);
    }

    @Transactional
    public void banMember(String callerEmail, Long groupId, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        findGroupOr404(groupId);

        membershipRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .ifPresent(membershipRepository::delete);

        if (!banRepository.existsByGroupIdAndUserId(groupId, targetUserId)) {
            GroupBan ban = new GroupBan();
            ban.setGroupId(groupId);
            ban.setUserId(targetUserId);
            ban.setBannedById(caller.getId());
            banRepository.save(ban);
        }

        runLifecycleChecks(groupId);
    }

    @Transactional
    public void unbanMember(String callerEmail, Long groupId, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        findGroupOr404(groupId);

        GroupBan ban = banRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not banned from this group"));
        banRepository.delete(ban);
    }

    @Transactional
    public GroupResponse addCategory(String callerEmail, Long groupId, Long categoryId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        Group group = findGroupOr404(groupId);

        if (!groupCategoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not exist");
        }
        if (categoryLinkRepository.findByGroupIdAndCategoryId(groupId, categoryId).isEmpty()) {
            GroupCategoryLink link = new GroupCategoryLink();
            link.setGroupId(groupId);
            link.setCategoryId(categoryId);
            categoryLinkRepository.save(link);
        }

        return toGroupResponse(group, caller.getId());
    }

    @Transactional
    public GroupResponse removeCategory(String callerEmail, Long groupId, Long categoryId) {
        User caller = findUserByEmail(callerEmail);
        requireAdmin(groupId, caller.getId());
        Group group = findGroupOr404(groupId);

        categoryLinkRepository.deleteByGroupIdAndCategoryId(groupId, categoryId);

        return toGroupResponse(group, caller.getId());
    }

    /**
     * Distance + category-overlap ranked recommendations.
     *
     * Scoring approach (simple, MVP-reasonable, see GroupService class
     * javadoc / spec.md report for rationale): every non-banned group the
     * caller isn't already a member of is scored as
     * {@code score = matchingCategoryCount * EARTH_MAX_DISTANCE_KM - distanceKm}.
     * {@code EARTH_MAX_DISTANCE_KM} (~20,038 km, half of Earth's
     * circumference) is the largest distance two points on Earth can ever
     * be apart, so a single matching category always outranks zero
     * matching categories regardless of how far away either group is —
     * category overlap is the dominant signal, distance is the tie-
     * breaker/ordering signal both within an overlap tier and across the
     * zero-overlap tier. This avoids needing a normalized/weighted blend
     * of two differently-scaled units (km vs. category count) while still
     * keeping both signals influential, per the spec's "combination of
     * distance and category overlap" wording. No radius cutoff is applied
     * — all groups are scored and the top N returned — since group volume
     * is expected to be small at MVP scale.
     */
    public List<GroupRecommendationResponse> recommendGroups(String callerEmail, double latitude, double longitude, int limit) {
        User caller = findUserByEmail(callerEmail);

        Set<Long> preferredCategoryIds = userCategoryPreferenceRepository.findByUserId(caller.getId()).stream()
                .map(UserCategoryPreference::getCategoryId)
                .collect(Collectors.toSet());

        Set<Long> memberGroupIds = membershipRepository.findByUserId(caller.getId()).stream()
                .map(GroupMembership::getGroupId)
                .collect(Collectors.toSet());
        Set<Long> bannedGroupIds = bannedGroupIdsForUser(caller.getId());

        List<Group> candidates = groupRepository.findAll().stream()
                .filter(g -> !memberGroupIds.contains(g.getId()))
                .filter(g -> !bannedGroupIds.contains(g.getId()))
                .toList();

        List<GroupCategoryLink> allLinks = categoryLinkRepository.findByGroupIdIn(
                candidates.stream().map(Group::getId).toList());
        Map<Long, List<Long>> categoryIdsByGroup = allLinks.stream()
                .collect(Collectors.groupingBy(GroupCategoryLink::getGroupId,
                        Collectors.mapping(GroupCategoryLink::getCategoryId, Collectors.toList())));

        record Scored(Group group, double distanceKm, int matchCount, double score) {}

        List<Scored> scored = candidates.stream()
                .map(g -> {
                    double distanceKm = haversineKm(latitude, longitude, g.getLatitude(), g.getLongitude());
                    List<Long> groupCategoryIds = categoryIdsByGroup.getOrDefault(g.getId(), List.of());
                    int matchCount = (int) groupCategoryIds.stream().filter(preferredCategoryIds::contains).count();
                    double score = matchCount * EARTH_MAX_DISTANCE_KM - distanceKm;
                    return new Scored(g, distanceKm, matchCount, score);
                })
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();

        Map<Long, List<CategoryResponse>> categoryResponsesByGroup = new HashMap<>();
        for (Scored s : scored) {
            List<Long> ids = categoryIdsByGroup.getOrDefault(s.group().getId(), List.of());
            categoryResponsesByGroup.put(s.group().getId(),
                    groupCategoryRepository.findAllById(ids).stream().map(CategoryResponse::from).toList());
        }

        return scored.stream()
                .map(s -> GroupRecommendationResponse.from(
                        s.group(),
                        membershipRepository.countByGroupId(s.group().getId()),
                        categoryResponsesByGroup.get(s.group().getId()),
                        s.distanceKm(),
                        s.matchCount()))
                .toList();
    }

    /**
     * Top-6-by-member-count public recommendation feed for the unauthenticated
     * landing page (see specs/public-group-recommendations/spec.md). No
     * caller identity, location, or preferences are involved — every group in
     * the system is eligible, ranked by member count descending, tie-broken
     * by createdAt descending (newer wins), then id ascending, and truncated
     * to the top {@value #PUBLIC_RECOMMENDATION_LIMIT}.
     */
    public List<PublicGroupRecommendationResponse> getPublicRecommendations() {
        Map<Long, Long> memberCountByGroupId = membershipRepository.findAll().stream()
                .collect(Collectors.groupingBy(GroupMembership::getGroupId, Collectors.counting()));

        List<Group> topGroups = groupRepository.findAll().stream()
                .sorted(Comparator
                        .comparingLong((Group g) -> memberCountByGroupId.getOrDefault(g.getId(), 0L))
                        .reversed()
                        .thenComparing(Group::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(Group::getId))
                .limit(PUBLIC_RECOMMENDATION_LIMIT)
                .toList();

        List<GroupCategoryLink> links = categoryLinkRepository.findByGroupIdIn(
                topGroups.stream().map(Group::getId).toList());
        Map<Long, List<Long>> categoryIdsByGroup = links.stream()
                .collect(Collectors.groupingBy(GroupCategoryLink::getGroupId,
                        Collectors.mapping(GroupCategoryLink::getCategoryId, Collectors.toList())));

        Map<Long, List<CategoryResponse>> categoryResponsesByGroup = new HashMap<>();
        for (Group g : topGroups) {
            List<Long> ids = categoryIdsByGroup.getOrDefault(g.getId(), List.of());
            categoryResponsesByGroup.put(g.getId(),
                    groupCategoryRepository.findAllById(ids).stream().map(CategoryResponse::from).toList());
        }

        return topGroups.stream()
                .map(g -> PublicGroupRecommendationResponse.from(
                        g,
                        memberCountByGroupId.getOrDefault(g.getId(), 0L),
                        categoryResponsesByGroup.get(g.getId())))
                .toList();
    }

    // ---- lifecycle rules (design.md §6b) ----

    /**
     * Runs synchronously, inside the same transaction as the membership
     * removal that triggered it (leave/kick/ban) — see design.md §6b for
     * why this is not a {@code @Scheduled} sweep.
     *
     * <ol>
     *   <li>If member count is now 0, delete the group (and its children)
     *       — no one is left to promote.</li>
     *   <li>Else if admin count is now 0, promote exactly one member:
     *       prefer the longest-tenured member (earliest {@code joinedAt})
     *       whose {@code User.lastSeenAt} is within the last 7 days; if no
     *       member qualifies, fall back to the single longest-tenured
     *       member overall.</li>
     * </ol>
     */
    private void runLifecycleChecks(Long groupId) {
        long memberCount = membershipRepository.countByGroupId(groupId);
        if (memberCount == 0) {
            deleteGroupAndChildren(groupId);
            return;
        }

        long adminCount = membershipRepository.countByGroupIdAndIsAdminTrue(groupId);
        if (adminCount > 0) {
            return;
        }

        promoteSuccessor(groupId);
    }

    private void promoteSuccessor(Long groupId) {
        List<GroupMembership> membersByTenure = membershipRepository.findByGroupIdOrderByJoinedAtAsc(groupId);
        if (membersByTenure.isEmpty()) {
            return;
        }

        Map<Long, User> usersById = usersById(membersByTenure.stream().map(GroupMembership::getUserId).toList());
        Instant onlineThreshold = Instant.now().minus(ONLINE_WITHIN_DAYS, ChronoUnit.DAYS);

        GroupMembership successor = membersByTenure.stream()
                .filter(m -> {
                    User u = usersById.get(m.getUserId());
                    return u != null && u.getLastSeenAt() != null && u.getLastSeenAt().isAfter(onlineThreshold);
                })
                .findFirst()
                .orElse(membersByTenure.get(0));

        successor.setAdmin(true);
        membershipRepository.save(successor);
    }

    /**
     * Deletes a group and every row that references it. There's no
     * database-level {@code ON DELETE CASCADE} configured (the schema is
     * Hibernate-managed via {@code ddl-auto: update}, not hand-written DDL),
     * so child rows are deleted explicitly, in dependency order, inside
     * this same transaction — avoids both orphaned rows and FK violations.
     * {@code GroupChatMessage} cleanup was added in Slice 3, and
     * {@code Activity} cleanup was added in Slice 5, per the note this
     * javadoc previously left for whichever slice introduced each entity.
     */
    private void deleteGroupAndChildren(Long groupId) {
        membershipRepository.deleteByGroupId(groupId);
        banRepository.deleteByGroupId(groupId);
        categoryLinkRepository.deleteByGroupId(groupId);
        groupChatMessageRepository.deleteByGroupId(groupId);
        activityRepository.deleteByGroupId(groupId);
        groupRepository.deleteById(groupId);
    }

    // ---- helpers ----

    private GroupResponse toGroupResponse(Group group, Long callerUserId) {
        long memberCount = membershipRepository.countByGroupId(group.getId());
        boolean isMember = membershipRepository.existsByGroupIdAndUserId(group.getId(), callerUserId);
        boolean isAdmin = membershipRepository.findByGroupIdAndUserId(group.getId(), callerUserId)
                .map(GroupMembership::isAdmin)
                .orElse(false);
        List<Long> categoryIds = categoryLinkRepository.findByGroupId(group.getId()).stream()
                .map(GroupCategoryLink::getCategoryId)
                .distinct()
                .toList();
        List<CategoryResponse> categories = groupCategoryRepository.findAllById(categoryIds).stream()
                .map(CategoryResponse::from)
                .toList();
        return GroupResponse.from(group, memberCount, isAdmin, isMember, categories);
    }

    private Set<Long> bannedGroupIdsForUser(Long userId) {
        return banRepository.findByUserId(userId).stream()
                .map(GroupBan::getGroupId)
                .collect(Collectors.toSet());
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    private void requireAdmin(Long groupId, Long userId) {
        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));
        if (!membership.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only group admins can perform this action");
        }
    }

    private Group findGroupOr404(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private Map<Long, User> usersById(List<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
