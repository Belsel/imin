---
status: verified
owner: analyst
created: 2026-07-07
---

# Public Group Recommendations

## Problem

The user asked for the public landing page to show "recommended groups."
This was clarified directly with the user: the target is the public,
logged-out `frontend/src/routes/LandingPage.tsx` (rendered at `/` for
unauthenticated visitors per `RootRoute` in `frontend/src/App.tsx`), **not**
the authenticated dashboard `HomePage`.

`specs/landing-page/spec.md` (status: verified) deliberately built that page
as static marketing copy only: "The page must not perform any new data
fetching or backend calls," and `LandingPage.tsx`'s docstring says the same
("Purely static marketing content — no data fetching, no backend calls").
This spec is an intentional, explicit relaxation of that constraint — its
whole purpose is to add one backend call and one dynamic section to that
page — and both the landing-page spec and the component docstring need to be
updated to say so (see Requirement 6).

Every group-recommendation path that exists today requires an authenticated
identity: `GET /api/groups/recommendations`
(`backend/src/main/java/com/imin/backend/group/GroupController.java`) takes
`@AuthenticationPrincipal Jwt jwt` plus caller-supplied `latitude`/
`longitude`, and `GroupService#recommendGroups`
(`backend/src/main/java/com/imin/backend/group/GroupService.java`) scores
every non-banned, non-joined group using the caller's
`UserCategoryPreference` rows and caller-supplied location. None of that
exists for an anonymous visitor — there is no user row, no stored category
preferences, no known location, no session. This spec defines what
"recommended" means with zero visitor context, and specs a brand-new public
(no-auth) endpoint to serve it. Because this is new, completely
unauthenticated internet-facing surface (not just "a new endpoint" — no
account, no rate limiting, no accountability for the caller), it needs the
same rigor as any other public API surface: an exact response shape, an
exact deterministic ranking rule, and an explicit decision on what data is
safe to hand to an anonymous caller.

### Grounding facts (verified in code, not re-derived)

- `Group` (`backend/src/main/java/com/imin/backend/group/Group.java`) has no
  visibility/privacy field of any kind — confirmed by grepping the `group`
  package for `visibility`, `isPrivate`, `isPublic`: no matches other than a
  code comment about banned-user visibility. There is no concept of a
  "private" group anywhere in this codebase today.
- Practical consequence: every existing authenticated user can already see
  every group's `name`, `description`, exact `latitude`/`longitude`, member
  count, and categories, via `GET /api/groups/search` (blank `q` returns
  `groupRepository.findAll()` minus banned/already-member groups) and
  `GET /api/groups/recommendations`. Group *membership* is gated (chat,
  member list, ban list); group *existence and these summary fields* are
  not. This spec does not add a privacy tier to the data model — one has
  never existed — but see the coordinate decision below for where this spec
  applies extra caution specifically for the *anonymous* case.
- `Group.latitude`/`longitude` is captured from the creating user's own
  device geolocation at group-creation time (`Group.java` class javadoc).
  For a small/home-based group, exact coordinates can approximate the
  creator's home location. This is already visible to any authenticated
  user today (low barrier: just requires an account); this spec is about
  handing it to anyone on the internet with zero account and zero
  accountability, which is a materially bigger exposure step even though
  the underlying access-control model hasn't changed. See Requirement 1 for
  the resolution.
- `backend/src/main/java/com/imin/backend/config/SecurityConfig.java`
  currently `permitAll()`s only `/api/auth/**`, `/oauth2/**`, `/login/**`,
  `/error`; every other path requires an authenticated JWT. The new
  endpoint needs an explicit new `permitAll()` entry.
- There is no app-level rate limiting or bot-mitigation infrastructure
  anywhere in the backend today (the only "rate limit" hits in the codebase
  are for the external routing provider in `RoutingService`/
  `RoutingException`, unrelated). This spec does not add any — see Out of
  scope — which is a known, explicitly-flagged residual risk of adding new
  unauthenticated surface, not an oversight.

## Requirements

### 1. New public endpoint

- **Path/method**: `GET /api/groups/public-recommendations` — sibling to
  the existing `GET /api/groups/recommendations`, following the same
  `/api/groups/{verb}` naming convention as `/search` and `/mine`.
- **Auth**: none. Must be reachable with no `Authorization` header, and must
  be added as an explicit new `permitAll()` entry in `SecurityConfig`'s
  `authorizeHttpRequests`.
- **Request**: takes **no** request parameters of any kind — no query
  string, no body, no cookies read for logic. This is a hard requirement
  (see Requirement 4, non-personalization) as well as a cacheability
  requirement: identical requests must always be eligible to produce
  identical responses so the endpoint can sit behind an HTTP/CDN cache in
  principle (implementing that cache layer is out of scope — see Out of
  scope).
- **Method/side effects**: read-only `GET`; must not write to the database
  or mutate any state (no view-count increment, no analytics row, nothing).
- **Response**: a JSON array (top-level, not wrapped in an envelope object,
  consistent with `GET /api/groups/recommendations`'s
  `List<GroupRecommendationResponse>` shape) of at most 6 objects (see
  Requirement 2 for why 6 and the exact selection rule), each containing
  exactly these fields:
  - `id` (number)
  - `name` (string)
  - `description` (string, nullable — same nullability as today's
    `Group.description`)
  - `latitude` (number, **rounded to 2 decimal places**)
  - `longitude` (number, **rounded to 2 decimal places**)
  - `memberCount` (number)
  - `categories` (array of the same category shape used elsewhere, e.g.
    `CategoryResponse` — id + name)

  Fields intentionally **excluded** from this response, unlike the
  authenticated `GroupRecommendationResponse`: `distanceKm` and
  `matchingCategoryCount` (both are meaningless without a visitor location/
  preferences — including them would either require fabricating fake
  personalization or leaking implementation noise), and `createdAt` (used
  internally for tie-breaking ranking only; no need to hand it to an
  anonymous caller).

  **Coordinate-exposure decision (resolved, not left open)**: round
  `latitude`/`longitude` to 2 decimal places (~1.1 km precision at the
  equator) for this endpoint specifically, rather than returning the exact
  stored value as the authenticated endpoints do. Rationale: (a) anonymous
  access removes the one soft deterrent that exists today — having to
  create an account before you can see any group's coordinates at all —
  so this is a real increase in ease of harvesting a small/home-based
  group's approximate creator location, even though no formal privacy tier
  is being violated; (b) rounding is enough to convey "there's a group near
  you" (the actual marketing value of showing location on a landing page)
  without being enough to pinpoint a residential address; (c) it requires
  no data-model or access-control change — it's a response-serialization
  detail scoped to this one new endpoint, so it doesn't touch or complicate
  the existing authenticated behavior (which is intentionally left as-is;
  changing it is out of scope for this spec). This is a judgment call, not
  a codebase-derived fact — flagged here explicitly in case the user or
  Leader wants a different precision or wants exact coordinates instead;
  proceeding with 2-decimal rounding as the default so the pipeline isn't
  blocked.

### 2. Ranking and selection rule (exact, deterministic)

- **Universe**: every `Group` row in the system (there is no membership,
  ban, or visibility concept that applies to an anonymous caller, so no
  filtering is possible or needed beyond "exists").
- **Rank**: order all groups by current member count **descending**.
- **Tie-break 1**: among groups with equal member count, order by
  `createdAt` **descending** (newer groups win ties) — this keeps a
  brand-new group from being permanently buried behind every
  equally-sized older group forever.
- **Tie-break 2**: among groups with equal member count *and* identical
  `createdAt` (should not occur in practice, but must be handled for full
  determinism/testability), order by `id` **ascending**.
- **Result size**: return the top **6** groups from this ordering. 6 is
  small enough to fit a single non-scrolling section on the landing page
  (e.g. a 2x3 or 3x2 grid) and large enough to look like a real, populated
  product rather than a sparse teaser.
- **Fewer than 6 groups exist**: return all of them (e.g. 2 groups in the
  system → response array has 2 elements). Never pad the response with
  duplicate, fake, or placeholder groups.
- **Zero groups exist**: return an empty array (`[]`) with `200 OK` — not
  an error status.
- Why member count as the primary signal (not "recently active" or
  "newest"): member count is a robust, well-understood "social proof"
  signal for a cold visitor deciding whether a product has any real
  activity behind it; it's cheap to compute (group membership counts are
  already computed elsewhere in `GroupService`, e.g.
  `toGroupResponse`/lifecycle checks) with no need to join against chat
  messages or activities, keeping the endpoint's query shape simple and
  genuinely cacheable; and it's fully deterministic with no clock-skew or
  "what counts as active" ambiguity the way a message/activity-timestamp
  based signal would introduce.

### 3. Non-personalization (hard constraint)

- This path must not use, read, infer, or derive any per-visitor signal —
  no IP-based geolocation, no cookies, no device fingerprinting, no
  `Authorization` header inspection even if one happens to be present. The
  response for a given point in time must be identical for every caller,
  by construction (no visitor-specific branch in the implementation), which
  is also what makes it cache/CDN-friendly.

### 4. Frontend: landing page integration

- `LandingPage.tsx` gets one new section that fetches
  `GET /api/groups/public-recommendations` (no auth header, no params) and
  renders the returned groups (name, description, member count, categories,
  and/or rounded location — exact visual treatment left to architect/
  implementer per the `frontend-aesthetics` skill, same latitude the
  original landing-page spec left for its own visual design).
- **Placement constraint**: the new section must be placed **after** the
  existing hero band (which contains the wordmark, tagline, pitch, and both
  `/register`/`/login` CTAs) — never above or inside the hero — so that the
  primary CTAs are never pushed below the fold on the initial hero view by
  this new section. This spec does not mandate the section sit above or
  below the existing "How ImIn works" steps section; that ordering is an
  architect/implementer visual-design decision.
- **Click-through**: for an anonymous visitor, clicking/tapping a
  recommended group in this section must navigate to `/register` (not to
  a group detail page — every group detail route is behind
  `<ProtectedRoute>` and would just redirect an anonymous visitor to
  `/login` anyway, which is a confusing dead end for a visitor who just
  clicked something that looked like content). `/register` was chosen over
  `/login` as the click target because the whole point of showing this
  section is conversion of a new, cold visitor — same reasoning as the
  hero's primary CTA already being `/register`.
- **Loading behavior**: the fetch must not block or delay the initial
  render of the hero section — the hero (wordmark, tagline, CTAs) must
  paint immediately regardless of this fetch's state. The new section
  either renders once the fetch resolves with at least one group, or does
  not render at all (see next bullet) — no layout-shifting spinner is
  required to sit in the hero's critical path.
- **Empty/error/slow-fetch behavior (must degrade gracefully)**: if the
  fetch fails (network error, non-2xx status, timeout) or resolves with an
  empty array, the new section must not render at all — no error banner,
  no broken empty box, no unhandled exception that could break the rest of
  the page. The rest of the static landing page (hero, CTAs, "How ImIn
  works") must render correctly and be fully usable regardless of this
  fetch's outcome. This page is the front door for cold visitors and must
  never appear broken because of a backend hiccup on this one optional
  section.

### 5. Backend security wiring

- Add `/api/groups/public-recommendations` as an explicit new entry in
  `SecurityConfig`'s `permitAll()` list (alongside `/api/auth/**`,
  `/oauth2/**`, `/login/**`, `/error`).

### 6. Required updates to existing specs/docstrings

- `specs/landing-page/spec.md`'s "Out of scope" bullet "Any new backend
  endpoints or API calls" must be updated to note the exception introduced
  by this spec (the one `GET /api/groups/public-recommendations` fetch),
  so the two specs don't contradict each other.
- `frontend/src/routes/LandingPage.tsx`'s docstring ("Purely static
  marketing content — no data fetching, no backend calls") must be updated
  to reflect that the page now performs exactly one, specific, unauthenticated
  fetch for the recommended-groups section, and no other data fetching.

## Out of scope

- Any personalization of this specific public path (see Requirement 3) —
  personalized recommendations for logged-in users continue to be served
  exclusively by the existing `GET /api/groups/recommendations`, unchanged.
- Pagination, infinite scroll, or a "see more groups" link out of this
  section for anonymous visitors.
- A group detail page (or any variant of one) reachable by an anonymous
  visitor — clicking a recommended group routes to `/register` (see
  Requirement 4), not to any group content.
- Admin controls, curation, or manual "featured group" selection — the
  rendered set is always the deterministic top-6-by-member-count
  computation in Requirement 2, with no manual override mechanism.
- Any caching/CDN implementation (e.g. `Cache-Control` headers, a reverse
  proxy, edge caching config) — this spec only requires the endpoint's
  *behavior* be cache-safe (no per-visitor variance, no side effects); an
  actual caching layer is a separate, later concern.
- Rate limiting, bot mitigation, or abuse protection for this new
  unauthenticated endpoint. The codebase has no existing app-level rate
  limiting infrastructure to extend, and building one is a materially
  larger effort than this feature. This is flagged as a known residual
  risk of adding new no-auth surface, not an oversight — if the user or
  Leader wants this addressed before implementation, it should be scoped as
  its own follow-up spec rather than folded into this one.
- Any change to the existing authenticated `GET /api/groups/recommendations`
  or `GET /api/groups/search` endpoints, their response shapes, or their
  (exact, unrounded) coordinate exposure — this spec adds a new path
  alongside them and does not modify either.
- Changing `Group`'s data model (no new visibility/privacy column) — this
  spec is deliberately implemented as a response-shaping decision on one
  new endpoint, not a data-model change (see Requirement 1's coordinate
  rationale).

## Acceptance criteria

- [ ] `GET /api/groups/public-recommendations` with no `Authorization`
      header returns `200 OK` (not `401`/`403`).
- [ ] The response body is a JSON array of at most 6 objects; each object
      contains exactly `id`, `name`, `description`, `latitude`, `longitude`,
      `memberCount`, `categories` — and does not contain `distanceKm`,
      `matchingCategoryCount`, or `createdAt`.
- [ ] `latitude`/`longitude` in the response are rounded to 2 decimal
      places (verified against the unrounded stored `Group` values in a
      test fixture).
- [ ] Given a test fixture of groups with distinct, known member counts,
      the response is ordered by member count descending; given two groups
      with equal member counts and different `createdAt`, the one with the
      later `createdAt` appears first.
- [ ] Given a test fixture with only 2 groups in the system, the response
      contains exactly those 2 groups (no padding/fake entries); given a
      fixture with 0 groups, the response is `[]` with `200 OK`.
- [ ] The endpoint accepts no query parameters — a request with an
      arbitrary/no query string produces the same response as any other
      request at the same point in time (no branch on caller-supplied or
      caller-derived data).
- [ ] `SecurityConfig`'s `permitAll()` list includes
      `/api/groups/public-recommendations`.
- [ ] The landing page (`frontend/src/routes/LandingPage.tsx`) renders a
      new section displaying the fetched groups when the endpoint returns
      at least one group.
- [ ] The new section is positioned after the hero band, never above or
      inside it — the hero's `/register`/`/login` CTAs remain visible
      without scrolling on the initial view, unaffected by the new
      section's presence.
- [ ] Clicking/tapping a rendered group in this section navigates to
      `/register`.
- [ ] With the fetch mocked/forced to fail (network error or non-2xx), the
      rest of the landing page (hero, tagline, CTAs, "How ImIn works")
      still renders correctly, the new section does not render, and no
      unhandled exception is thrown.
- [ ] With the fetch mocked to resolve with `[]`, the new section does not
      render (no empty container/placeholder shown).
- [ ] `specs/landing-page/spec.md`'s "Out of scope" section is updated to
      note the one-fetch exception introduced by this spec.
- [ ] `frontend/src/routes/LandingPage.tsx`'s docstring no longer claims
      "no data fetching, no backend calls" unconditionally; it accurately
      describes the one recommended-groups fetch this spec adds.
- [ ] `frontend` builds and typechecks cleanly (`npm run build` /
      `tsc --noEmit`) with the new section and fetch in place.
- [ ] Backend builds and existing tests pass (`./mvnw test` or equivalent)
      with the new endpoint, DTO, and `SecurityConfig` entry in place.

## Design notes

Additive design on top of the existing `group` package and `LandingPage.tsx`
— no existing file's *behavior* changes except the two files Requirement 6
already calls out (this spec's own cross-spec doc edit, made directly by the
architect, is recorded at the bottom of this section).

### Backend

**New file**: `backend/src/main/java/com/imin/backend/group/dto/PublicGroupRecommendationResponse.java`

```java
package com.imin.backend.group.dto;

import com.imin.backend.category.dto.CategoryResponse;
import com.imin.backend.group.Group;

import java.util.List;

/**
 * Group summary for the public, unauthenticated landing-page recommendation
 * feed (see specs/public-group-recommendations/spec.md). Deliberately
 * narrower than GroupRecommendationResponse: no distanceKm/
 * matchingCategoryCount (meaningless without a visitor location/preferences)
 * and no createdAt (ranking-internal only, see GroupService#getPublicRecommendations).
 * latitude/longitude are rounded to 2 decimal places here, at response-
 * construction time only -- the stored Group row is never read back,
 * mutated, or persisted with a rounded value.
 */
public record PublicGroupRecommendationResponse(
        Long id,
        String name,
        String description,
        double latitude,
        double longitude,
        long memberCount,
        List<CategoryResponse> categories
) {
    public static PublicGroupRecommendationResponse from(Group group, long memberCount,
                                                          List<CategoryResponse> categories) {
        return new PublicGroupRecommendationResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                round2(group.getLatitude()),
                round2(group.getLongitude()),
                memberCount,
                categories
        );
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
```

Field ordering/naming intentionally mirrors `GroupRecommendationResponse`
minus the excluded fields, so the two DTOs read as siblings. `latitude`/
`longitude` are plain `double` (not boxed `Double`) since `Group`'s columns
are `nullable = false` — no null-handling needed, unlike `description`.

**`GroupRepository`/`GroupMembershipRepository`/`GroupCategoryLinkRepository`**:
no changes. All three already expose what's needed
(`GroupRepository.findAll()`, `GroupMembershipRepository.findAll()` —
inherited from `JpaRepository`, not currently called elsewhere in
`GroupService` but requires no new method — and
`GroupCategoryLinkRepository.findByGroupIdIn`, already used by
`recommendGroups`).

**`GroupService` — new method, in-memory ranking (not a repository
query/projection)**:

```java
private static final int PUBLIC_RECOMMENDATION_LIMIT = 6;

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
```

No `@Transactional` — matches the existing convention that read-only
methods (`searchGroups`, `getMyGroups`, `recommendGroups`) aren't annotated;
only mutating methods are.

Why in-memory over a repository projection/`@Query`, given member count is
itself the sort key (unlike `recommendGroups`, where member count is only
computed *after* limiting, since distance/category-overlap is the sort key
there): a hand-rolled JPQL query with `COUNT(membership) ... GROUP BY
group.id ORDER BY count DESC, group.createdAt DESC, group.id ASC` would
work, but would be new query-writing surface for a one-off endpoint, and
this codebase's precedent (`recommendGroups`'s own javadoc: "no radius
cutoff is applied... since group volume is expected to be small at MVP
scale") already accepts fetch-everything-then-sort-in-memory for group
ranking. The one N+1 risk — calling `countByGroupId` once per group — is
avoided here without a new query by fetching **all** `GroupMembership` rows
in a single `findAll()` and grouping/counting in memory (same batching
principle `recommendGroups` already uses for category links via
`findByGroupIdIn`, just applied to the full membership table since there's
no per-group predicate to batch against). This keeps the method to exactly
two unconditional queries (`groupRepository.findAll()`,
`membershipRepository.findAll()`) plus one bounded query
(`categoryLinkRepository.findByGroupIdIn`, called only on the post-limit
top 6, not all groups) regardless of total group count — simple, and
genuinely cheap at the volumes this app expects. If group/membership
volume ever grows enough for this to matter, revisit as a `@Query`
projection then; not a concern worth solving preemptively here.

**Coordinate rounding — confirmed placement**: rounding happens exclusively
inside `PublicGroupRecommendationResponse.round2`, applied to the value
read from `group.getLatitude()`/`getLongitude()` at DTO-construction time.
`Group` itself, `GroupRepository`, and every other DTO
(`GroupResponse`, `GroupRecommendationResponse`) are completely untouched —
there is no code path in this design that calls `group.setLatitude(...)`
or persists a rounded value. Implementer: do not "simplify" this by
rounding earlier (e.g. in a repository query or a shared helper touching
the entity) — the exact stored value must remain intact for every other
endpoint.

**`GroupController`** — new method, placed directly after `recommendGroups`:

```java
@GetMapping("/public-recommendations")
public ResponseEntity<List<PublicGroupRecommendationResponse>> getPublicRecommendations() {
    return ResponseEntity.ok(groupService.getPublicRecommendations());
}
```

No `@AuthenticationPrincipal Jwt` parameter and no `@RequestParam`s of any
kind — this is the whole point (Requirement 1/3). Path is exactly
`/api/groups/public-recommendations` (sibling of `/api/groups/recommendations`
under the class's existing `@RequestMapping("/api/groups")`). Spring's path
matching prefers the literal segment over the `@GetMapping("/{id}")`
handler regardless of declaration order, so no route-shadowing concern —
placement next to `recommendGroups` is purely for readability.

**`SecurityConfig`** — exact one-line change to the existing
`requestMatchers(...).permitAll()` call:

```java
// before
.requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/error").permitAll()
// after
.requestMatchers("/api/auth/**", "/oauth2/**", "/login/**", "/error",
        "/api/groups/public-recommendations").permitAll()
```

**Test plan**

`GroupServiceTest` (existing `@SpringBootTest`/H2/`@Transactional` pattern
— append new `@Test` methods to the existing class, no new test file).
Because member count and `createdAt` are both ranking-significant here
(unlike existing tests in this file, which only exercise membership via
`groupService.joinGroup`/`banMember`), add a small private helper that
constructs `Group` rows **directly via the autowired `GroupRepository`**
(bypassing `groupService.createGroup`, which would force every group to
have a creator-admin membership and today's `Instant.now()` `createdAt`,
making exact counts/tie-breaks awkward to set up) — e.g.
`private Group rawGroup(String name, Instant createdAt)` that builds and
saves a `Group` with an explicit `setCreatedAt(createdAt)`
(`updatable = false` only suppresses the field from UPDATE statements, not
the initial INSERT, so this works). Add memberships the same direct way via
`GroupMembershipRepository.save(...)` to get exact counts per group.
Cases to cover (map 1:1 to this spec's acceptance criteria):
- Ordered by member count descending (3 groups, distinct counts).
- Equal member count, different `createdAt` → later `createdAt` first.
- Equal member count *and* equal `createdAt` → lower `id` first (construct
  two groups with the same explicit `Instant`).
- 8 groups in the system → response has exactly 6, and they're the correct
  top 6 by the ordering rule (not just "any 6").
- 2 groups in the system → response has exactly those 2.
- 0 groups in the system → response is an empty list, no exception.
- `latitude`/`longitude` in the response are rounded to 2 decimals — assert
  against a fixture with a value that actually changes under rounding
  (e.g. `51.5074123` → `51.51`), and separately assert the *stored* `Group`
  row fetched fresh from `groupRepository` after the call still has the
  original unrounded value (guards against the "don't mutate the entity"
  rule above).
- A group with no membership rows at all still appears with `memberCount`
  0 (exercises the `getOrDefault(..., 0L)` path — no group should ever be
  silently dropped for having zero members).

`GroupControllerTest` (existing `@SpringBootTest` + `@AutoConfigureMockMvc`
+ MockMvc pattern — append to the existing class):
- `GET /api/groups/public-recommendations` with **no** `Authorization`
  header → `status().isOk()` (the existing file's
  `createGroupUnauthenticatedIsRejected`-style test is the pattern to
  mirror, just asserting the opposite outcome here).
- Response shape: after creating at least one group via the existing
  `createGroupViaApi` helper, assert via `jsonPath` that `$[0].id`,
  `$[0].name`, `$[0].memberCount`, `$[0].categories` exist and that
  `$[0].distanceKm`, `$[0].matchingCategoryCount`, `$[0].createdAt` do
  **not** (`jsonPath("$[0].distanceKm").doesNotExist()`, etc.).
- Empty case: `groupRepository.deleteAll()` then assert `status().isOk()`
  and `jsonPath("$.length()").value(0)`.

No new `SecurityConfig`-specific test file exists in this codebase today
(grep found none) and none is warranted here — the "no auth header
succeeds" `GroupControllerTest` case above already exercises the real
filter chain end-to-end, which is the only thing worth asserting about the
`permitAll()` entry.

### Frontend

**`frontend/src/lib/apiClient.ts`** — add, in the existing "Group
endpoints" section, alongside `GroupRecommendationResponse`:

```ts
export interface PublicGroupRecommendationResponse {
  id: number
  name: string
  description: string | null
  latitude: number
  longitude: number
  memberCount: number
  categories: CategoryResponse[]
}

/**
 * Unauthenticated recommended-groups feed for the public landing page (see
 * specs/public-group-recommendations/spec.md). Deliberately passes
 * skipAuth so no Authorization header is ever sent, even if a stale token
 * happens to be in localStorage from a prior session on this device —
 * the backend ignores any Authorization header on this path regardless
 * (Requirement 3), but skipping it client-side too keeps this call
 * trivially cache/CDN-safe and avoids sending a token the endpoint will
 * never look at.
 */
export function getPublicRecommendations(): Promise<PublicGroupRecommendationResponse[]> {
  return apiFetch<PublicGroupRecommendationResponse[]>('/api/groups/public-recommendations', {
    method: 'GET',
    skipAuth: true,
  })
}
```

This reuses the codebase's one existing fetch convention
(`apiFetch`/`ApiError` in `apiClient.ts`) — every other frontend API call
goes through this module (confirmed by grep), so this is a straight
extension, not a new pattern.

**Component structure — new file, not inline**:
`frontend/src/components/PublicGroupsSection.tsx`. `LandingPage.tsx` today
has zero state and zero effects (pure static JSX); this feature introduces
the page's *first* data-fetching/stateful behavior. Encapsulating it in
its own component, imported and rendered as a single `<PublicGroupsSection />`
line in `LandingPage.tsx`, keeps that file's own docstring update
(Requirement 6) accurate with minimal surface ("the page renders one
additional component that performs one fetch" is easier to state precisely
than inlining a `useEffect`/`useState` block into an otherwise-static
component). It also matches this codebase's existing extraction convention
— `GroupListItem`, `MapView`, `NavBar` are all separately-filed components
used by page-level routes rather than inlined, and `GroupListItem`
specifically is the closest sibling to what this component renders (a
group summary card). `GroupListItem` itself is **not** reused here: its
`Link` target is hardcoded to `/groups/${group.id}` (a route behind
`<ProtectedRoute>`), which is exactly wrong for this spec's click-through
requirement (must go to `/register`) — reusing it would require a prop to
override the link target for one caller, which is more indirection than
just writing the ~30 lines of new card markup this component needs.

**State handling** (graceful degradation, per Requirement 4):

```tsx
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getPublicRecommendations } from '../lib/apiClient'
import type { PublicGroupRecommendationResponse } from '../lib/apiClient'

export default function PublicGroupsSection() {
  const [groups, setGroups] = useState<PublicGroupRecommendationResponse[] | null>(null)

  useEffect(() => {
    let cancelled = false
    getPublicRecommendations()
      .then((results) => {
        if (!cancelled) setGroups(results)
      })
      .catch(() => {
        // Network error / non-2xx / timeout: degrade to "nothing to show",
        // same as an empty array — never surface an error here (spec
        // Requirement 4: this section must never look broken).
        if (!cancelled) setGroups([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!groups || groups.length === 0) {
    return null
  }

  return (
    <section className="mx-auto max-w-4xl px-6 py-16">
      <h2 className="mb-8 text-center text-2xl font-bold font-display text-text">
        Real groups, right now
      </h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {groups.map((group) => (
          <Link
            key={group.id}
            to="/register"
            className="block rounded-2xl border border-border bg-surface p-4 shadow-sm transition-colors motion-safe:hover:border-primary/60 motion-safe:hover:shadow"
          >
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-text font-body">{group.name}</span>
              <span className="shrink-0 text-sm text-text-muted font-body">
                {group.memberCount} member{group.memberCount === 1 ? '' : 's'}
              </span>
            </div>
            {group.description && (
              <p className="mt-1 line-clamp-2 text-sm text-text-muted font-body">{group.description}</p>
            )}
            {group.categories.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {group.categories.map((category) => (
                  <span
                    key={category.id}
                    className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary"
                  >
                    {category.name}
                  </span>
                ))}
              </div>
            )}
          </Link>
        ))}
      </div>
    </section>
  )
}
```

Notes on this sketch:
- Deliberately **no loading state/spinner** — per Requirement 4 ("no
  layout-shifting spinner is required"), the component simply renders
  `null` until the fetch resolves with a non-empty array, then renders
  once. This is a slight simplification versus `GroupsListPage.tsx`'s
  pattern (which does track `isLoading` and shows "Loading…" text) —
  intentional, since that page is an authenticated dashboard where showing
  loading state is expected UX, whereas this is a marketing page where
  Requirement 4 explicitly opts out of that.
- Both the success and failure paths converge on the same `groups` state
  shape (`[]` for "nothing to show"), so the render logic only needs one
  check (`!groups || groups.length === 0`) rather than separate
  `error`/`loading`/`data` branches like `GroupsListPage.tsx` uses — that
  richer 3-state pattern is appropriate there because that page *does*
  need to explain failures to an already-signed-up user; this section is
  optional decoration for a cold visitor and must never show an error per
  spec, so collapsing error → empty is correct here, not a corner cut.
  `if (import.meta.env.DEV) console.error(...)` in the `.catch` would be a
  reasonable optional addition for local debugging, but isn't required by
  the spec — implementer's call.
- `line-clamp-2` requires Tailwind's typography-adjacent utility; if not
  already enabled in this project's Tailwind v4 setup, drop it and let the
  description wrap naturally — it's a nice-to-have, not a requirement.
  (Not verified against this project's exact Tailwind v4 config; flagged
  for implementer to confirm at build time, since `tsc`/`vite build`
  passing is a hard acceptance criterion — an unsupported utility class
  won't fail the build either way since Tailwind utilities are just
  strings, but it may render as a no-op if the plugin isn't present, which
  is a cosmetic-only risk, not a build-breaking one.)

**Visual layout decision**: a straightforward responsive card grid (1 col
mobile → 2 → 3, up to 6 cards), reusing `GroupListItem`'s existing card
styling tokens (`bg-surface`/`border-border`/`rounded-2xl`/`shadow-sm`,
hover `border-primary/60`) rather than inventing new card chrome, and
reusing the same category-pill treatment (`bg-primary/10 text-primary`
pills). This deliberately does **not** try to be a second "signature
element" alongside the numbered timeline — the timeline is `LandingPage`'s
one intentional visual risk (per its own implementation notes), and
Requirement 4's "browse a few real examples" framing plus the spec's own
"a straightforward card grid is likely fine and expected" guidance both
point at restraint here: a grid of real, on-brand cards *is* the content
(the proof point), so it doesn't need a decorative treatment competing
with the timeline's. Coordinates are **not** rendered on the card in this
design — bare rounded lat/lng numbers (e.g. "51.51, -0.13") aren't
meaningful or attractive UI without a map to place them on, and Requirement
4 only requires location "and/or" be shown, so name + description + member
count + categories is the chosen subset. (The API still returns
`latitude`/`longitude` per Requirement 1 regardless of whether this
component renders them — a future map-pin treatment could use them without
a backend change.)

**Placement**: between the hero and the existing "How ImIn works" timeline
section (i.e. hero → `PublicGroupsSection` → timeline), not after the
timeline. Rationale: this section's entire value is being *concrete proof*
("here are real groups, right now") — that lands better immediately after
the hero's abstract pitch and before the reader is asked to absorb the
3-step timeline explanation, following a hook → proof → explanation flow.
It also means the click-through CTA (every card → `/register`) reinforces
the hero's own CTAs while the visitor's attention/intent is still highest,
just below the fold, rather than competing for attention at the very
bottom of the page after the explanation has already been read. Both hero
and timeline markup are unchanged; only one new line (`<PublicGroupsSection />`)
is inserted between the closing `</header>` and the existing `<main>`
block. Because the component renders `null` on failure/empty, the
hero-then-timeline flow degrades to exactly today's layout with no gap or
placeholder box when the fetch doesn't produce data — satisfying the
"never appear broken" requirement structurally, not just via conditional
rendering.

**Click-through**: confirmed — every card is itself the `<Link to="/register">`
(whole-card click target, not a smaller CTA button within it), matching
the existing `GroupListItem`/hero-CTA pattern of using `react-router`'s
`Link` directly rather than a manual `onClick`/`navigate()`.

**Docstring update** (`frontend/src/routes/LandingPage.tsx`, exact new
wording, replacing the current second sentence):

> Public, unauthenticated landing page rendered at "/" for logged-out
> visitors (see the auth-state branch in App.tsx). Mostly static marketing
> content: the only data fetching this page performs is one unauthenticated
> `GET /api/groups/public-recommendations` call, made by the child
> `<PublicGroupsSection />` component, to show a few real groups as a trust
> signal — no other backend calls happen anywhere on this page.

### Cross-spec doc edit (Requirement 6)

Made directly by the architect (small doc edit, not code):
`specs/landing-page/spec.md`'s "Out of scope" bullet "Any new backend
endpoints or API calls." is updated to note this spec's one-fetch
exception, so the two specs no longer contradict each other. See that
file's diff.

### Risks / judgment calls flagged for implementer

- The `line-clamp-2` utility class (cosmetic only — confirm it renders as
  expected under this project's Tailwind v4 setup; harmless no-op if not,
  won't break the build).
- `Group.setCreatedAt(...)` before the initial `save()` in new
  `GroupServiceTest` fixtures relies on `updatable = false` only
  suppressing UPDATE statements, not the initial INSERT — this is standard
  JPA/Hibernate behavior but worth a sanity-check the first time it's run,
  since nothing in this codebase's existing tests currently exercises it.
- No caching/rate-limiting is added here (both explicitly out of scope per
  the spec) — this design keeps the endpoint's *behavior* cache-safe
  (no per-visitor branch, no side effects, `GET`-only) so a future caching
  layer can be added without revisiting this design, but no such layer
  exists yet; this is a known residual risk carried over from the spec,
  not something this design introduces or resolves.

## Implementation notes

Implemented exactly per the architect's design notes above; no product
decisions re-derived (endpoint path, ranking rule, 2-decimal coordinate
rounding, `/register` click-through, no personalization, no new
visibility/privacy data-model field all carried over unchanged).

**Backend**:
- New `backend/src/main/java/com/imin/backend/group/dto/PublicGroupRecommendationResponse.java`
  — copied verbatim from the design's code block (record with
  `id`/`name`/`description`/`latitude`/`longitude`/`memberCount`/`categories`,
  a `from(Group, long, List<CategoryResponse>)` factory, and a private
  `round2` helper applied only at DTO-construction time).
- `GroupService.java`: added `PUBLIC_RECOMMENDATION_LIMIT = 6` alongside the
  existing `EARTH_MAX_DISTANCE_KM` constant, and a new
  `getPublicRecommendations()` method placed directly after `recommendGroups`
  (before the `// ---- lifecycle rules ----` section), matching the design's
  in-memory ranking approach: one `membershipRepository.findAll()` grouped/
  counted by group id, one `groupRepository.findAll()` sorted by member count
  descending / `createdAt` descending / `id` ascending and limited to 6, then
  one bounded `categoryLinkRepository.findByGroupIdIn` call against only the
  post-limit top 6. No `@Transactional` (read-only, matches
  `searchGroups`/`recommendGroups` convention).
- `GroupController.java`: added `GET /public-recommendations` directly after
  `recommendGroups`, no `@AuthenticationPrincipal Jwt` and no
  `@RequestParam`s, returning `ResponseEntity.ok(groupService.getPublicRecommendations())`.
- `SecurityConfig.java`: added `/api/groups/public-recommendations` to the
  existing `permitAll()` list's `requestMatchers(...)` call, alongside
  `/api/auth/**`, `/oauth2/**`, `/login/**`, `/error`.
- `GroupServiceTest.java`: added a `rawGroup(String name, Instant createdAt)`
  helper (saves a `Group` directly via `GroupRepository`, bypassing
  `groupService.createGroup`) and an `addMembers(Group, int)` helper (saves
  `GroupMembership` rows directly via `GroupMembershipRepository`), plus 8 new
  `@Test` methods covering: descending member-count ordering; the
  `createdAt`-descending tie-break for equal member counts, and the
  `id`-ascending tie-break for equal member count *and* equal `createdAt`;
  top-6-of-8 selection (asserting the exact expected 6 ids, not just count);
  fewer-than-6 (2 groups → 2 results); zero groups → empty list; coordinate
  rounding (`51.5074123` → `51.51`, `-0.1277654` → `-0.13`) verified against
  the still-unrounded value read back fresh from `groupRepository` after the
  call; and a zero-membership group still appearing with `memberCount: 0`.
- `GroupControllerTest.java`: added 3 new `@Test` methods — no-`Authorization`-
  header request returns `200`; response shape assertions (`id`/`name`/
  `memberCount`/`categories` exist, `distanceKm`/`matchingCategoryCount`/
  `createdAt` do not, via `jsonPath(...).doesNotExist()`); and the empty-
  database case (`groupRepository.deleteAll()` → `200` + `$.length()` == 0).
  No new `SecurityConfig`-specific test file, per the design's rationale (the
  no-auth-header controller test already exercises the real filter chain).

**Frontend**:
- `apiClient.ts`: added the `PublicGroupRecommendationResponse` interface and
  `getPublicRecommendations()` function (with the design's exact docstring)
  in the existing "Group endpoints" section, alongside
  `GroupRecommendationResponse`/`recommendGroups`. Uses `apiFetch` with
  `skipAuth: true`, no other options.
- New `frontend/src/components/PublicGroupsSection.tsx` — copied the design's
  sketch essentially verbatim: `useState<PublicGroupRecommendationResponse[] | null>(null)`,
  a mount-time `useEffect` fetch with a `cancelled` guard, `.catch()`
  collapsing any failure to `setGroups([])`, and a single
  `if (!groups || groups.length === 0) return null` gate. Renders a
  responsive 1/2/3-column card grid (up to 6 cards), each card a whole-card
  `<Link to="/register">` styled with the same tokens as `GroupListItem`
  (`bg-surface`/`border-border`/`rounded-2xl`/`shadow-sm`, hover
  `border-primary/60`) and the same category-pill treatment
  (`bg-primary/10 text-primary`). Coordinates are not rendered on the card,
  per the design's rationale (bare rounded lat/lng numbers aren't meaningful
  without a map). `line-clamp-2` was kept as-is — Tailwind v4's core utility
  set includes `line-clamp-*` natively (no plugin needed), and it rendered
  correctly in the production build.
- `LandingPage.tsx`: imported `PublicGroupsSection` and inserted
  `<PublicGroupsSection />` as a single line between the closing `</header>`
  and the existing `<main>` block (hero → recommended groups → "How ImIn
  works" timeline), per the design's placement/ordering rationale. Updated
  the docstring's second sentence to the exact wording specified in the
  design notes, replacing the old "no data fetching, no backend calls"
  claim.
- `specs/landing-page/spec.md`: confirmed the architect's cross-spec
  "Out of scope" edit (the one-fetch exception) was already present — no
  further change needed there.

**Deviations from spec/design**: None. Implemented as specified, including
the exact method signatures, DTO shape, ranking comparator, docstring
wording, and component structure the architect provided.

**Verification commands run**:
- `cd backend && ./mvnw -Dtest=GroupServiceTest,GroupControllerTest test` —
  53 tests, 0 failures, 0 errors (22 in `GroupControllerTest`, 31 in
  `GroupServiceTest`, including all new cases).
- `cd backend && ./mvnw test` (full suite) — 179 tests, 0 failures, 0 errors,
  `BUILD SUCCESS`.
- `cd frontend && npm run build` (`tsc -b && vite build`) — completed with no
  errors.
- `cd frontend && npx oxlint` — one pre-existing warning
  (`react(only-export-components)` in `AuthContext.tsx`, unrelated to this
  change, same one flagged in the landing-page spec's own implementation
  notes); no new warnings or errors.

## Verification

Independent, from-scratch execution against a running system, not a re-read
of the implementer/reviewer's reports. Environment had no Docker daemon
access (`com.docker.service` present but not startable — no permission) and
no local Postgres, so the live backend was run against an H2 database
instead of the Neon/Postgres instance the app targets in prod — H2 is the
same engine the existing test suite already uses (`application-test.yml`),
Spring Data JPA abstracts the SQL, and the ranking/rounding logic under test
is pure Java (`Comparator`/`Math.round`) with no Postgres-specific behavior,
so this substitution does not weaken the check. `Spring-boot:run` was
started with `-Dspring-boot.run.useTestClasspath=true` (to put H2 on the
runtime classpath) and env vars pointing `DATABASE_URL` at a file-based H2
DB (`AUTO_SERVER=TRUE`), which let an external `org.h2.tools.Shell` process
seed rows directly into the real running app's real database concurrently.

**1. Live no-auth + shape check (`GET /api/groups/public-recommendations`, real running Spring Boot app, port 8099, H2-backed)**
- Seeded via direct SQL (bypassing the app's write path, to seed known
  fixture data without needing email-verified accounts): 2 groups —
  "Camden Runners" (2 members, category "Running", lat/lng
  `51.5074123`/`-0.1277654`) and "Quiet Sketch Club" (1 member, no category,
  no description, lat/lng `51.4`/`-0.2`).
- Plain `curl` with **no** `Authorization` header → `HTTP/1.1 200`, body:
  `[{"id":1,"name":"Camden Runners","description":"A friendly running crew.","latitude":51.51,"longitude":-0.13,"memberCount":2,"categories":[{"id":13,"name":"Running"}]},{"id":2,"name":"Quiet Sketch Club","description":null,"latitude":51.4,"longitude":-0.2,"memberCount":1,"categories":[]}]`.
  PASS: `200` (not 401/403); ordered by member count descending (2 before
  1); `51.5074123`/`-0.1277654` rounded to `51.51`/`-0.13`; `null`
  description preserved, not omitted; empty `categories: []` for the
  uncategorized group, not omitted or null.
- Programmatically diffed each returned object's key set against the
  spec's exact field list: both objects have exactly
  `{id, name, description, latitude, longitude, memberCount, categories}` —
  no `distanceKm`, `matchingCategoryCount`, or `createdAt` present, and a
  substring grep of the raw response body for those three names returned no
  matches. PASS.
- Deleted all groups (`DELETE FROM groups` + child tables) and re-hit the
  endpoint → `200` with body `[]`. PASS (zero-groups case, not an error
  status).
- Query-parameter/non-personalization check, done live and stronger than a
  spot-check: minted a **genuinely valid** HS256 JWT by hand (same
  algorithm/secret `JwtConfig`/`JwtService` use), confirmed it was in fact
  valid by calling the real authenticated `GET /api/auth/me` with it
  (returned the expected subject), then called
  `GET /api/groups/public-recommendations?latitude=99&longitude=99&limit=1`
  with that valid bearer token attached → response body was byte-for-byte
  identical to the no-auth, no-query-param call. PASS: confirms both "no
  query params accepted/no branch on them" and Requirement 3's
  "no Authorization header inspection even if one happens to be present"
  live, with a real valid token, not just an absent one. (Separately noted,
  not a spec violation: sending a *malformed/garbage* bearer token, e.g.
  `Bearer totally-invalid-garbage-token`, does produce a `401` — this is
  Spring Security's `BearerTokenAuthenticationFilter` rejecting an
  undecodable token before the `permitAll()` authorization decision is even
  reached, standard framework behavior on any path once a resource-server
  filter chain is configured, not something this endpoint's own code
  controls or a violation of "no Authorization header required.")
- `SecurityConfig.java` inspected directly: `permitAll()` list includes
  `"/api/groups/public-recommendations"` alongside the other public paths.
  Confirmed both by reading the file and by the live no-auth 200 above
  (which exercises the real filter chain, not just the config line).

**2. Frontend dev-server smoke check** (`npm run dev -- --port 5183 --strictPort`, backend **not** running at this point — a genuine, not simulated, unreachable-backend condition):
- Server started cleanly, no errors in log.
- `GET /` → `200`. `GET /src/App.tsx`, `GET /src/routes/LandingPage.tsx`,
  `GET /src/components/PublicGroupsSection.tsx` → all `200`, all transform
  through Vite with no module/transform errors in the response body.
  PASS: confirms the new component resolves and the app boots with it in
  place.
- No browser tool is available in this environment (same limitation noted
  in `specs/landing-page/spec.md`'s own Verification section), so the
  client-side fetch-failure/render-null behavior was not observed live in
  a DOM; it is verified by code review instead: `PublicGroupsSection.tsx`'s
  `.catch()` unconditionally calls `setGroups([])` on any fetch failure
  (network error, non-2xx, timeout all reach the same `.catch`), and the
  component's only render branch is
  `if (!groups || groups.length === 0) return null`, so a failed fetch and
  an empty-array fetch are structurally identical at render time — there is
  no separate error branch that could throw or render a broken box. This
  matches the landing-page spec's own precedent of substituting code review
  for a live screenshot where no browser tool exists.

**3. Full test-suite re-run (independent, not trusted from prior reports)**
- `cd backend && ./mvnw test` → **179 tests, 0 failures, 0 errors,
  BUILD SUCCESS**. Matches the implementer's reported count.
- `cd backend && ./mvnw -Dtest=GroupServiceTest,GroupControllerTest test` →
  **53 tests, 0 failures, 0 errors, BUILD SUCCESS**. Spot-checked the
  actual test method names in `GroupServiceTest.java` against this spec's
  acceptance criteria (member-count descending order; `createdAt`
  descending tie-break then `id` ascending tie-break; top-6-of-8 exact
  selection; fewer-than-6; zero-groups; coordinate rounding verified
  against the still-unrounded stored row; zero-membership group still
  included at `memberCount: 0`) — all present, 1:1 with the criteria.
- `cd frontend && npm run build` (`tsc -b && vite build`) → completed with
  no errors, no type errors.

**4. Static/grep checks**
- Grep for `lorem`/`Lorem`/`Feature One`/`Widget`/`placeholder`
  (case-insensitive) in `PublicGroupsSection.tsx` → zero matches.
- Grep for a group-detail-route link pattern (`/groups/${`, `to="/groups`)
  in `PublicGroupsSection.tsx` → zero matches; the file's only `<Link>`
  target is the literal string `to="/register"`, confirmed by direct read.
- `specs/landing-page/spec.md`'s "Out of scope" section already contains
  the required one-fetch exception (confirmed by direct read of that
  file — Requirement 6/cross-spec edit is in place).
- `frontend/src/routes/LandingPage.tsx`'s docstring no longer makes an
  unconditional "no data fetching, no backend calls" claim; it now
  describes exactly the one `GET /api/groups/public-recommendations` fetch
  performed by the child `<PublicGroupsSection />` (confirmed by direct
  read).

**Acceptance criteria walk-through**: every checklist item in this spec was
independently verified true by one of the checks above (live HTTP for the
no-auth/shape/ranking/rounding/empty/query-param items; the re-run test
suite for the fixture-based ordering/tie-break/fewer-than-6/zero-group
service-level assertions the reviewer already covered; code review + dev
server smoke check for the frontend placement/click-through/graceful-
degradation items, per the landing-page spec's own no-browser-tool
precedent; grep for the doc/content items; a clean `./mvnw test` and
`npm run build` for the build items). No criterion failed. No criterion was
left unchecked without saying so above (the one honest gap: client-side
render behavior on fetch failure/empty result was verified by code reading,
not a live DOM observation, because no browser tool exists in this
environment — same limitation as `specs/landing-page/spec.md`).

All acceptance criteria in this spec are met. Status set to `verified`.
