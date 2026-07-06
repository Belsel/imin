# ImIn MVP — Technical Design

Linked from `specs/mvp/spec.md` ("Design notes"). This document covers data
model, API layout, file/module layout, and the two flagged architecture
decisions (routing, admin succession) plus lighter notes on polling and
email verification.

Status: ready for implementation. Spec status has been advanced to
`in-progress`.

## 0. Baseline — what already exists vs. what implementer builds

**Already exists (do not redesign, extend instead):**

- `backend/` Spring Boot 4.1 skeleton, Java 21 (see flag in §7), Maven.
  - `com.imin.backend.user`: `User` entity (id, email, passwordHash,
    displayName, provider enum LOCAL/GOOGLE, createdAt), `AuthProvider`
    enum, `UserRepository`.
  - `com.imin.backend.auth`: `AuthController` (`POST /api/auth/register`,
    `POST /api/auth/login`, `GET /api/auth/me`), `AuthService`,
    `dto.{RegisterRequest,LoginRequest,AuthResponse}`.
  - `com.imin.backend.security`: `JwtService` (HMAC HS256 via Nimbus,
    1h expiry, claims `uid`/`name`), `OAuth2LoginSuccessHandler` (Google
    login → finds-or-creates `User`, issues JWT, redirects to
    `{frontendUrl}/oauth2/callback?token=...`).
  - `com.imin.backend.config`: `JwtConfig` (encoder/decoder beans),
    `SecurityConfig` (stateless JWT resource server, CORS from
    `app.frontend-url`, `/api/auth/**`, `/oauth2/**`, `/login/**` public,
    everything else authenticated).
  - `application.yml` / `.env.example`: DB, JWT secret, Google OAuth
    client id/secret, frontend URL, port.
  - `render.yaml` + `Dockerfile`: deploy config, already wired for the
    above env vars.
- `frontend/` Vite + React 19 + TypeScript + Tailwind v4 + Leaflet skeleton.
  - `src/App.tsx` — placeholder shell rendering `<MapView />`.
  - `src/components/MapView.tsx` — bare `MapContainer`/`TileLayer`/`Marker`
    with a hardcoded center, no routing, no props.
  - `src/lib/leafletIconFix.ts` — Vite asset-URL fix for default marker
    icons (keep using this for any new marker icons).
  - `src/main.tsx`, `src/index.css` — Tailwind v4 CSS-first entry, no
    router, no global state, no API client.
  - `.env.example` — `VITE_API_BASE_URL` only.

**Does not exist yet (implementer builds all of it):** every domain entity
beyond `User` (groups, categories, memberships, bans, activities, friends,
blocks, group chat messages, DM threads/messages), every controller beyond
`AuthController`, all frontend routing/pages/components beyond the
placeholder, the routing-API integration, email verification, and the
admin-succession logic. None of this exists in any partial form — there is
no other branch or dead code to reconcile with.

## 1. Data model

All new JPA entities live under `com.imin.backend.<domain>` packages (see
§3). IDs are `Long` identity columns, consistent with `User`. Timestamps are
`Instant`. This section describes columns/constraints/relationships; exact
Lombok/JPA annotation style should mirror the existing `User` entity
(`@Entity @Table @Getter @Setter @NoArgsConstructor`, no Lombok `@Builder`
since `User` doesn't use one).

### 1.1 User (extend existing entity)

**Decision: there is no separate `accountName` column.** The spec defines
"account name" as simply the user's email address, for both LOCAL and
Google OAuth signup. The existing `email` column (`NOT NULL UNIQUE`,
already present on `User`) already satisfies every requirement the spec
places on "account name" — uniqueness and immutability — with no
duplication. Wherever the spec/this design says "account name," read it as
"the `email` column"; no derivation, no collision-suffixing, no extra
write path. (Rejected alternative: keep a denormalized `accountName` column
copied from `email` at registration, as protection against a hypothetical
future "change your email" feature. Rejected because no such feature exists
anywhere in this spec — that would be speculative schema for a requirement
that doesn't exist, and it creates exactly the kind of two-columns-that-must-
stay-in-sync surface that's worth avoiding when nothing forces it. If a
future spec ever adds email-change, that's the point to revisit, e.g. by
introducing a true immutable `accountName` snapshot at that time — not
before.)

Add to the existing `users` table/`User` entity:

| Column | Type | Notes |
|---|---|---|
| `bio` | `text`, nullable | Free-text, user-editable, clearable (empty string or null both acceptable — implementer's call, just be consistent). |
| `emailVerified` | `boolean NOT NULL DEFAULT false` | `true` immediately for `GOOGLE` provider (Google already verifies email); starts `false` for `LOCAL` until verification flow completes. |
| `lastSeenAt` | `Instant`, nullable | Updated on authenticated request (see §1.4 for how this feeds the 7-day online rule). |

`email` is already `NOT NULL UNIQUE` on the existing entity (per the
baseline auth skeleton, §0) and is already immutable in practice (no update
path exists for it today); no schema change is needed to make it serve as
the account name — only the application-level framing ("account name" =
`email`) and the absence of any accountName-specific input/derivation
logic.

`displayName` already exists and already satisfies "changeable, non-unique."

New related entities (one-directional edges + preferences), all
`ManyToOne` to `User` by id (no bidirectional JPA collections needed —
query via repositories, not navigation, to avoid N+1/proxy surprises):

**`UserCategoryPreference`** (join table, simple `@Entity` not `@ElementCollection`
so it has its own id and is easy to query):
- `id`, `userId` (FK → users), `categoryId` (FK → group_categories)
- Unique constraint on `(userId, categoryId)`.

**`Friendship`** (one-directional "A added B"):
- `id`, `followerId` (FK → users, the adder), `followeeId` (FK → users, the
  added)
- Unique constraint on `(followerId, followeeId)`.
- No self-friend constraint is required by the spec; implementer may add a
  `CHECK (follower_id <> followee_id)` as a reasonable conventional
  default, not a spec requirement.
- Unfriending = delete the row. No soft-delete needed (spec has no
  "history of past friends" requirement).

**`Block`** (one-directional "A blocks B"):
- `id`, `blockerId` (FK → users), `blockedId` (FK → users)
- Unique constraint on `(blockerId, blockedId)`.
- Unblocking is out of scope per spec text for users (spec only specifies
  *adding* a block for users; group bans are the only block-like relation
  with an explicit unban requirement). Implementer should still expose a
  delete path for the block row at the repository level since "a user can
  block another user" doesn't preclude reversal and leaving no way to
  reverse a mis-click is a poor default — but no acceptance criterion
  requires an unblock endpoint, so it is **not required for MVP**; treat as
  optional/implementer's judgment, not a gap to flag back.

### 1.2 GroupCategory (fixed taxonomy)

**`GroupCategory`**: `id`, `name` (`varchar UNIQUE NOT NULL`). Seeded via a
Spring Boot data initializer (e.g., a `@Component` `ApplicationRunner` or
`src/main/resources/data.sql` with `spring.sql.init.mode=always`, gated so
it only inserts when the table is empty, or simply an idempotent
upsert-by-name on startup) — not exposed via any create/update/delete API,
satisfying "users/groups cannot create, rename, or otherwise manage
categories." A literal seed list (e.g., Sports, Music, Food & Drink,
Outdoors, Gaming, Arts & Crafts, Tech, Books & Writing, Fitness, Travel,
Volunteering, Social) is a content decision, not an architecture decision —
implementer/analyst can finalize wording; design just needs the mechanism
(seeded, immutable via API).

### 1.3 Group

**`Group`**: `id`, `name` (`varchar NOT NULL`, not globally unique — spec
never requires unique group names, only a unique account identifier per
user, which is just `email`), `description` (`text`, nullable),
`latitude`/`longitude` (`Double`, both set together, non-null after
creation), `createdAt`.

**Resolved (was a flag in the prior design pass — see §7):** a group's
location is not a manually-entered field on the create-group form. It is
captured automatically from the creating user's current location (device
geolocation, the same client-side mechanism used elsewhere for distance-
based recommendations) at the moment the group is created, and stored as a
one-time, immutable snapshot — no admin edit/refresh capability exists for
it afterward. Mechanism: the frontend's create-group form reads the
browser's current position via the Geolocation API (the same call site/hook
used for recommendation requests elsewhere in the app — implementer should
share one geolocation-acquisition utility rather than duplicating the
browser API call) and submits `latitude`/`longitude` alongside `name`/
`description` in the `POST /api/groups` request body. The backend persists
exactly what it receives at creation time and never recomputes or accepts
updates to it — `PATCH /api/groups/{id}` (rename/description, §4) does not
accept latitude/longitude fields, enforcing immutability by omission, the
same pattern used for `email`/account-name immutability.

This location is what powers the distance component of group
recommendations (§4, `GET /api/groups/recommendations`).

**`GroupCategoryLink`** (join table, group ↔ category, many-to-many):
- `id`, `groupId` (FK → groups), `categoryId` (FK → group_categories)
- Unique constraint on `(groupId, categoryId)`.

### 1.4 GroupMembership

**`GroupMembership`**: `id`, `groupId` (FK → groups), `userId` (FK → users),
`isAdmin` (`boolean NOT NULL DEFAULT false`), `joinedAt` (`Instant NOT NULL`,
set at insert time — this is the tenure clock for succession ordering).

- Unique constraint on `(groupId, userId)` — a user can only have one
  membership row per group (membership uniqueness requirement).
- Row deleted on leave/kick; ban is a separate table (§1.5) so a
  ban can outlive — and indeed exist independently of — membership (a
  banned user has no membership row at all once kicked/banned; ban is what
  blocks rejoining).
- "Online within last 7 days" is evaluated off `User.lastSeenAt` at
  succession-decision time (see §5), not stored per-membership — no
  separate online-tracking column is needed on `GroupMembership` itself
  since online status is a user-level fact, not a per-group fact. (Naming
  this out explicitly because the task prompt mentions "online-tracking" as
  if it might be a membership-level column; it is more naturally a
  `User`-level column reused across all of a user's groups.)

### 1.5 GroupBan

**`GroupBan`**: `id`, `groupId` (FK → groups), `userId` (FK → users),
`bannedAt` (`Instant`), `bannedById` (FK → users, the admin who banned —
useful for any future audit need, cheap to add now).

- Unique constraint on `(groupId, userId)` — a user is either banned from a
  given group or not; presence of the row = banned. Unban = delete the row.
- Scoping: every ban check is `WHERE groupId = :g AND userId = :u`, so a
  ban in Group A structurally cannot affect Group B (satisfies the
  per-group scoping requirement directly via the schema, no extra logic
  needed).
- A banned user must not have a `GroupMembership` row for that group
  (banning implies removal). Enforce in the service layer: ban action =
  delete membership (if present) + insert ban row, in one transaction.

### 1.6 Activity

**`Activity`**: `id`, `groupId` (FK → groups), `ownerId` (FK → users), `name`
(`varchar NOT NULL`), `description` (`text`, nullable), `scheduledAt`
(`Instant NOT NULL`), `latitude`/`longitude` (`Double`, both nullable
together — "optional location"), `createdAt`.

No recurrence fields, no RSVP/attendance table — explicitly out of scope.

### 1.7 GroupChatMessage

**`GroupChatMessage`**: `id`, `groupId` (FK → groups), `senderId` (FK →
users), `body` (`text NOT NULL`), `sentAt` (`Instant NOT NULL`, indexed for
polling queries — `findByGroupIdAndSentAtAfterOrderBySentAtAsc` or
id-cursor equivalent, see §5 polling notes).

A group's chat is implicit (1 group : 1 chat via `groupId` FK) — no
separate `Chat` entity needed; "each group has exactly one chat" falls out
of messages simply being scoped by `groupId` rather than needing
their own join table indirection. This is the smallest design that
satisfies the requirement (rejected alternative: a standalone `Chat` entity
with a 1:1 to `Group` — adds a join and a row to manage for zero behavioral
benefit, since no spec requirement needs a chat to exist independently of
its group, e.g. there's no "create the chat before/separately from the
group" requirement).

### 1.8 Direct chat (DM)

**`DirectThread`**: `id`, `userAId` (FK → users), `userBId` (FK → users),
`createdAt`. Represents "the" shared conversation between an unordered pair
of users (spec: "at most one ongoing direct conversation between any two
given users").

- To make the pair unordered while keeping a clean unique constraint:
  normalize at write time so `userAId < userBId` (by numeric id) whenever a
  thread is created or looked up. A unique constraint on
  `(userAId, userBId)` then structurally prevents duplicate threads for the
  same pair regardless of who initiated first contact.
- Lookup-or-create pattern in the service layer: given two user ids, sort
  them, `findByUserAIdAndUserBId(...).orElseGet(() -> create...)`.

**`DirectMessage`**: `id`, `threadId` (FK → direct_threads), `senderId` (FK
→ users), `body` (`text NOT NULL`), `sentAt` (`Instant NOT NULL`, indexed).

Block check happens at send-time in the service layer (see §4 Direct chat
endpoints), not via a DB constraint — blocking is a dynamic, checked-at-
write-time business rule (and can change between messages), not a
structural invariant.

### 1.9 ER summary (textual)

```
User 1───* GroupMembership *───1 Group 1───* GroupCategoryLink *───1 GroupCategory
User 1───* GroupBan        *───1 Group
User 1───* Activity (owner) *───1 Group (via groupId)
User 1───* GroupChatMessage *───1 Group
User 1───* Friendship(follower) ... Friendship(followee) *───1 User
User 1───* Block(blocker)      ... Block(blocked)        *───1 User
User 1───* UserCategoryPreference *───1 GroupCategory
User *───* User  (via DirectThread, unordered pair, unique(userAId,userBId))
DirectThread 1───* DirectMessage *───1 User (sender)
```

## 2. File/module layout — backend

Following the existing convention of one top-level package per domain
concept under `com.imin.backend` (mirrors `user`, `auth`, `security`,
`config`):

```
backend/src/main/java/com/imin/backend/
  user/
    User.java                  (extend: bio, emailVerified, lastSeenAt — no accountName column, see §1.1)
    AuthProvider.java          (existing, unchanged)
    UserRepository.java        (extend: findByAccountName, existsByAccountName)
    UserController.java        (new — profile read/update endpoints)
    UserService.java           (new)
    dto/ ProfileResponse.java, UpdateProfileRequest.java, PublicProfileResponse.java
  auth/
    AuthController.java        (extend: /verify-email, /resend-verification)
    AuthService.java           (extend: registration issues verification token+email)
    dto/ ... (RegisterRequest unchanged in shape — email/password/displayName
              only; no accountName field, since email is the account name)
    EmailVerificationToken.java   (new entity: id, userId FK, token, expiresAt, usedAt)
    EmailVerificationTokenRepository.java
  social/
    Friendship.java, FriendshipRepository.java
    Block.java, BlockRepository.java
    SocialController.java      (friends + blocks endpoints)
    SocialService.java
  category/
    GroupCategory.java, GroupCategoryRepository.java
    CategorySeeder.java        (ApplicationRunner / data.sql)
    UserCategoryPreference.java, UserCategoryPreferenceRepository.java
    CategoryController.java    (GET /api/categories — read-only list)
  group/
    Group.java, GroupRepository.java
    GroupCategoryLink.java, GroupCategoryLinkRepository.java
    GroupMembership.java, GroupMembershipRepository.java
    GroupBan.java, GroupBanRepository.java
    GroupController.java       (CRUD, search, recommend, membership, admin actions, ban/unban)
    GroupService.java          (includes admin-succession logic, §5)
    dto/ ...
  activity/
    Activity.java, ActivityRepository.java
    ActivityController.java
    ActivityService.java
    dto/ ...
  chat/
    GroupChatMessage.java, GroupChatMessageRepository.java
    GroupChatController.java
    DirectThread.java, DirectThreadRepository.java
    DirectMessage.java, DirectMessageRepository.java
    DirectChatController.java
    ChatService.java / DirectChatService.java
    dto/ ...
  routing/
    RoutingController.java     (backend proxy to routing provider — see §6a)
    RoutingService.java
    dto/ RouteRequest.java, RouteResponse.java
  email/
    EmailService.java          (interface) + provider impl — see §6c
  security/                    (existing, unchanged except: lastSeenAt touch)
  config/                      (existing, unchanged)
```

`GroupService` is the natural home for admin-succession (§5) since it
already owns membership-mutation transactions (leave/kick/ban/delete).

A cross-cutting `LastSeenFilter` (or a small addition inside the existing
JWT resource-server filter chain) updates `User.lastSeenAt = now()` on any
authenticated request — cheapest place to capture "online" without a
dedicated heartbeat endpoint. Implementer's choice of filter vs. an
`@ControllerAdvice`-style interceptor; functionally equivalent.

## 3. File/module layout — frontend

No router, no API client, no auth context currently exist. Implementer
introduces:

```
frontend/src/
  lib/
    leafletIconFix.ts          (existing, unchanged)
    apiClient.ts               (new — fetch wrapper, attaches JWT from storage, base URL from VITE_API_BASE_URL)
  context/
    AuthContext.tsx            (new — holds JWT + current user, login/logout/register actions)
  routes/  (or pages/ — implementer's call; routes/ suggested since this needs a router)
    LoginPage.tsx, RegisterPage.tsx, OAuthCallbackPage.tsx, VerifyEmailPage.tsx
    ProfilePage.tsx
    GroupsListPage.tsx, GroupDetailPage.tsx, GroupChatPage.tsx (or tab within GroupDetailPage)
    ActivityDetailPage.tsx (or modal within GroupDetailPage)
    DirectChatListPage.tsx, DirectChatPage.tsx
    FriendsPage.tsx (friends + blocks management)
  components/
    MapView.tsx                (existing — extend with props: target lat/lng, onRouteRequest, etc.)
    RoutingControl.tsx         (new — wraps leaflet-routing-machine, see §6a)
    GroupCard.tsx, ActivityCard.tsx, ChatThread.tsx, MessageComposer.tsx, ...
  hooks/
    usePolling.ts              (new — generic interval-based GET polling hook, see §5 polling)
  App.tsx                       (extend — introduce React Router, wrap routes in AuthContext)
  main.tsx                       (extend — add BrowserRouter)
```

This needs `react-router` (or `react-router-dom`) added as a new
dependency — not currently in `package.json`. No backend-equivalent
addition needed (Spring MVC already does this job server-side).

## 4. API layout (REST, all under `/api`, JWT bearer auth except where noted)

Conventions: plural nouns, nested resources for group-scoped things,
`PATCH` for partial updates, standard 401/403/404. All endpoints below
require auth except `/api/auth/register`, `/api/auth/login`,
`/api/auth/verify-email`, `/oauth2/**` (already permitted in
`SecurityConfig`) and `/api/categories` (read-only reference data — fine to
leave public or authenticated, implementer's call, doesn't affect design).

**Auth** (`AuthController`, extends existing)
- `POST /api/auth/register` (existing shape, no new accountName field: takes
  `email`/`password`/`displayName` as today, creates unverified user, sends
  verification email — `email` itself is the account name, so there is no
  separate accountName input to add to this DTO at all)
- `POST /api/auth/login` (existing, unchanged; blocks login for unverified
  LOCAL accounts — see §6c for the gating rule, now stated directly with no
  dangling cross-reference)
- `GET /api/auth/verify-email?token=...` (new)
- `POST /api/auth/resend-verification` (new, optional but cheap to include)
- `GET /api/auth/me` (existing)
- Google OAuth2 flow unchanged in shape (`OAuth2LoginSuccessHandler`);
  extend it only to set `emailVerified=true` on first login. No accountName
  generation/derivation/collision-suffix step is needed — the Google
  profile's email (already the find-or-create key) simply is the account
  name, with nothing further to compute.

**Users / profile** (`UserController`, new)
- `GET /api/users/me` — full profile (own)
- `PATCH /api/users/me` — update displayName, bio, category preferences
  (`email` never accepted here — there is no email-change feature in this
  spec, so immutability of the account identifier holds simply by this
  endpoint never exposing an `email` field, consistent with today's
  baseline behavior)
- `GET /api/users/{id}` — public profile (no profile-hiding option exists,
  per spec — always visible)
- `GET /api/users/search?q=...` — supports friend-add / DM target lookup
  (spec doesn't explicitly require user search, but friends/DM features
  need a way to find a user; smallest addition that makes those features
  reachable — implementer may instead defer to group member lists/search as
  the only discovery path if preferred, but a search endpoint is the more
  conventional default)

**Friends / blocks** (`SocialController`, new)
- `GET /api/friends` — users the caller has added
- `POST /api/friends/{userId}` — add friend (idempotent: 200/204 if already
  added)
- `DELETE /api/friends/{userId}` — unfriend
- `GET /api/blocks` — users the caller has blocked
- `POST /api/blocks/{userId}` — block
- `DELETE /api/blocks/{userId}` — unblock (see §1.1 — not spec-required but
  cheap and sensible to include)

**Categories** (`CategoryController`, new, read-only)
- `GET /api/categories` — fixed taxonomy list

**Groups** (`GroupController`, new)
- `POST /api/groups` — create (caller becomes admin). Request body:
  `name`, `description`, plus `latitude`/`longitude` — the creator's
  current-location coordinates, obtained client-side via the browser
  Geolocation API (the same mechanism/utility used for recommendation
  requests, see §1.3) and sent up as part of this same request. There is no
  manual/optional location input field anywhere on the create-group form;
  the frontend acquires the coordinates itself and includes them
  automatically. The backend stores exactly what it receives and never
  recomputes or refreshes it afterward (see §1.3 for the immutability
  rule).
- `GET /api/groups/{id}` — detail (404/403 if caller is banned — banned
  users get no visibility, see §7 note on whether 403 vs 404 leaks ban
  state; recommend 404 to avoid confirming "you're banned" vs "doesn't
  exist", though this is a minor implementer judgment call, not a spec gap)
- `PATCH /api/groups/{id}` — rename/description (admin only)
- `DELETE /api/groups/{id}` — delete (admin only; also happens automatically
  per lifecycle rule, see §5)
- `GET /api/groups/search?q=...` — name search
- `GET /api/groups/recommendations` — distance + category-overlap ranked
  list; distance is computed from the caller's current location (acquired
  client-side the same way as for group creation, §1.3) against each
  group's stored `latitude`/`longitude` (§1.3) — both sides of the distance
  calculation now have a concrete source, no longer an open question
- `POST /api/groups/{id}/categories` / `DELETE /api/groups/{id}/categories/{categoryId}`
  — admin manages which fixed categories apply to the group
- `POST /api/groups/{id}/members` — join (403 if banned)
- `DELETE /api/groups/{id}/members/me` — leave (triggers §5 lifecycle checks)
- `DELETE /api/groups/{id}/members/{userId}` — kick (admin only; triggers §5)
- `POST /api/groups/{id}/admins/{userId}` — promote to admin (admin only —
  not explicitly required by spec but trivial/consistent to include since
  multi-admin groups exist; **not required for MVP**, omit if minimizing
  surface, implementer's call)
- `POST /api/groups/{id}/bans/{userId}` — ban (admin only; deletes
  membership + inserts ban row; triggers §5)
- `DELETE /api/groups/{id}/bans/{userId}` — unban (admin only)
- `GET /api/groups/{id}/members` — member list (members only)

**Activities** (`ActivityController`, new, nested under group)
- `GET /api/groups/{groupId}/activities` — list (members only)
- `POST /api/groups/{groupId}/activities` — create (any current member;
  caller becomes owner)
- `GET /api/groups/{groupId}/activities/{id}`
- `PATCH /api/groups/{groupId}/activities/{id}` — owner or any group admin
- `DELETE /api/groups/{groupId}/activities/{id}` — owner or any group admin

**Group chat** (`GroupChatController`, new)
- `GET /api/groups/{groupId}/messages?after={cursor}` — poll for new
  messages (members only, 403 if banned/non-member); cursor = last-seen
  message id or timestamp, client-driven
- `POST /api/groups/{groupId}/messages` — post text message (members only)

**Direct chat** (`DirectChatController`, new)
- `GET /api/dm/threads` — list the caller's threads (with other-party info,
  maybe last message preview)
- `GET /api/dm/threads/{otherUserId}` — get-or-create-on-read thread with a
  specific user (lazy creation keeps "no thread until first contact" clean
  without a separate explicit-create endpoint)
- `GET /api/dm/threads/{otherUserId}/messages?after={cursor}` — poll
- `POST /api/dm/threads/{otherUserId}/messages` — send (403 if recipient has
  blocked caller — checked at send time every time, not cached, since a
  block can be applied between polls)

**Routing** (`RoutingController`, new — see §6a for why this is proxied
server-side rather than called directly from the frontend)
- `GET /api/routing/directions?fromLat=&fromLng=&toLat=&toLng=` — proxies to
  the chosen routing provider, returns the provider's route geometry/steps
  (or a thin normalized subset) to the frontend.

## 5. Polling mechanism (chats)

No special backend infrastructure: the `GET .../messages?after=...`
endpoints above are plain REST reads with a cursor parameter. Frontend
`usePolling` hook wraps `setInterval` (suggest 3–5s while a chat view is
mounted, cleared on unmount) calling that endpoint and appending new
results to local state. No SSE, no long-polling, no WebSocket — this
satisfies the spec's explicit polling requirement with the least
infrastructure. Render's free-tier cold start means the *first* poll after
an idle period may be slow (dyno spin-up); no design mitigation is
required for MVP since the spec doesn't set a latency acceptance criterion
— this is a known, accepted free-tier trade-off, not a gap.

## 6. Key architecture decisions

### 6a. Leaflet routing/turn-by-turn navigation

**Recommendation: Leaflet Routing Machine (`leaflet-routing-machine`) on
the frontend, backed by OpenRouteService (ORS) Directions API, called
through a thin backend proxy endpoint (`GET /api/routing/directions`)
rather than directly from the browser.**

Plugin choice: `leaflet-routing-machine` is the de facto standard Leaflet
routing UI plugin (turn-by-turn instruction panel, route line, draggable
waypoints) and ships an `IRouter` abstraction so the backing routing
engine is swappable. It defaults to OSRM's public demo router, but third-
party `IRouter` implementations exist for ORS (e.g., `lrm-openrouteservice`)
— or, given we're proxying through our own backend anyway (see below), the
frontend can call a custom `IRouter` that just hits `/api/routing/directions`
and adapts the response shape, sidestepping the need for a third-party ORS
adapter package at all. Either is viable; calling our own proxy endpoint
directly (custom lightweight `IRouter`) is the leaner option since it
avoids pulling in an extra small, less-maintained adapter package.

Backing routing service choice — OSRM demo vs. OpenRouteService vs.
self-hosted OSRM vs. GraphHopper vs. Mapbox:

| Option | Verdict | Why |
|---|---|---|
| OSRM public demo server | Rejected as primary | No API key, but ToS caps at 1 req/sec, explicitly "non-commercial... no uptime/latency/data guarantees," and meant for casual testing, not an app's only routing backend. Fine as a local-dev fallback, not production-safe. |
| Self-hosted OSRM | Rejected | OSRM needs a preprocessed region extract (RAM-resident graph) — even a single-country extract is tight-to-impossible on Render free tier's 512MB RAM/shared CPU, and free tier sleeps on idle, so a long-lived routing process wouldn't even stay warm. Not feasible at this hosting tier. |
| GraphHopper free tier | Viable alternative | Has a free API tier, but smaller documented free quota and less mature Leaflet plugin ecosystem than ORS; no decisive advantage over ORS for this use case. |
| Mapbox Directions API | Rejected for MVP | Free tier exists but is usage-metered with a credit card required after a grace allotment, and pulls in a heavier non-Leaflet-native SDK story; inconsistent with "all map rendering is built on Leaflet" using OSM tiles already in `MapView.tsx`. |
| **OpenRouteService (ORS)** | **Chosen** | Clear, generous, well-documented free tier (2,500 req/day, 40,000/mo, no credit card), signup-only API key, designed for exactly this (directions/turn-by-turn with step instructions), and has existing Leaflet ecosystem support. Best balance of reliability + cost + effort for an MVP that won't have heavy traffic. |

**Why proxy through the backend instead of calling ORS directly from the
browser:** the ORS API key would otherwise ship in the frontend JS bundle
(Vite env vars prefixed `VITE_` are public at build time) and be visible to
any user — anyone could lift the key and exhaust the shared daily quota.
Routing the request through `RoutingController` keeps the key
server-side-only (`backend/.env.example` gets a new `ORS_API_KEY`
placeholder; never `VITE_`-prefixed). The added latency of one extra hop is
negligible relative to the routing API's own response time and Render's
cold-start variance.

Frontend: `MapView.tsx` (or a new `RoutingControl.tsx` wrapping it) accepts
a `target: {lat, lng}` prop; whenever a target is present, it adds a
Leaflet Routing Machine control configured with a custom `IRouter` that
calls `/api/routing/directions?fromLat=...&fromLng=...&toLat=...&toLng=...`
using the browser's geolocation (or a manually-entered "from" point if
geolocation is denied — implementer's UX call, not a spec requirement) as
the origin.

New env var: `backend/.env.example` gets `ORS_API_KEY=` (placeholder).

### 6b. Group admin-succession mechanism

**Recommendation: on-demand / event-triggered, executed synchronously
inside the same service-layer transaction that removes the group's last
admin — not a scheduled/polling sweep.**

Rationale:
- Render's free-tier web service sleeps after 15 minutes of inactivity and
  cold-starts on the next request. A `@Scheduled` cron-style sweep (e.g.,
  "check every N minutes for zero-admin groups") is unreliable here: if the
  dyno is asleep when the schedule would fire, Spring's in-process
  scheduler simply doesn't run — there is no guarantee of "catch-up" on
  wake, since `@Scheduled` is wall-clock-relative-to-process-uptime, not a
  durable cron stored externally. A user could be left in a zero-admin
  group indefinitely if no one ever causes a fresh request.
- An on-demand check has no such dependency: it runs exactly when the
  triggering action happens (last admin leaves / is kicked / is banned /
  account-deleted-while-admin), inside the request that's already live and
  the dyno that's already awake to serve it. This makes the invariant
  ("zero admins ⇒ promote someone, immediately") hold deterministically
  rather than probabilistically.
- It's also simpler: no extra infrastructure (no cron registration, no
  "last swept at" bookkeeping, no idempotency concerns from overlapping
  sweep runs), and cheaper to test (call the service method, assert the
  promotion happened) versus testing a timing-dependent background job.

**Concrete mechanism:** every mutation that can remove an admin —
`GroupService.leaveGroup`, `kickMember`, `banMember`, plus (if account
deletion is ever added — not in this spec, noted for completeness) account
deletion — performs, inside the same `@Transactional` method, after
deleting/demoting the membership row:

1. Delete the membership (leave/kick) or membership+insert-ban-row (ban),
   per §1.5.
2. Count remaining `GroupMembership` rows for the group.
   - If `0`: delete the `Group` (cascades to its memberships [already 0],
     bans, categories link rows, activities, chat messages — implementer
     must add `ON DELETE CASCADE` or equivalent `@OneToMany(cascade =
     CascadeType.REMOVE)`/explicit cleanup for each child table so group
     deletion doesn't orphan rows or throw FK violations). Done — no
     succession check needed, there's no one left to promote.
   - Else: count remaining admin memberships (`isAdmin = true`) for the
     group.
     - If `> 0`: nothing to do.
     - If `0`: run succession —
       a. Query remaining members ordered by `joinedAt ASC`, joined to
          `User.lastSeenAt`.
       b. Pick the first (earliest `joinedAt`) member whose
          `User.lastSeenAt >= now() - 7 days`.
       c. If none qualify, fall back to the single earliest-`joinedAt`
          member overall (already have this list from step a, just take
          index 0 without the `lastSeenAt` filter).
       d. Set that member's `GroupMembership.isAdmin = true`.

This is a single SQL query (order by `joinedAt`, filter/fallback in Java
over a small result set — group membership being unbounded in theory but
practically small enough per-group that loading all members for this
one-time succession check is fine; if a group somehow has tens of
thousands of members this could be optimized to two targeted queries, but
that's a non-issue for MVP scale) plus one update, all inside the existing
transaction — no new infra.

### 6c. Email verification flow

Token-based, standard pattern, no deep trade-off analysis warranted (per
prompt) but a provider must be picked since "send an email" isn't free
without one:

- New entity `EmailVerificationToken` (id, userId FK, token [UUID or
  random opaque string], expiresAt, usedAt nullable) in
  `com.imin.backend.auth`.
- On `LOCAL` registration: create user with `emailVerified=false`, generate
  a token (e.g., 24h expiry), send an email containing
  `{frontendUrl}/verify-email?token=...`.
- `GET /api/auth/verify-email?token=...` looks up the token, checks not
  expired/not used, sets `User.emailVerified=true`, marks token used.
- `EmailService` interface in `com.imin.backend.email`, with a concrete
  implementation behind it so the provider is swappable.
- **Provider pick: Resend** (or, equivalently viable, Brevo/SendGrid free
  tier) — Resend has a straightforward free tier (3,000 emails/month, 100/
  day), a simple REST API (no SMTP wrangling needed, fits a lightweight
  `RestClient`-based `EmailService` impl with no extra heavyweight
  dependency like `spring-boot-starter-mail`/JavaMail unless implementer
  prefers SMTP — either works), and is commonly used for exactly this
  "transactional verification email" use case. This is a low-stakes pick
  versus the routing decision — any free transactional-email provider with
  an API key would satisfy the requirement equally well; Resend is named
  to give implementer a concrete default rather than an open menu.
- New env vars: `backend/.env.example` gets `RESEND_API_KEY=` and
  `EMAIL_FROM_ADDRESS=` (placeholders).
- Gating: per spec, email verification gates **login** for unverified LOCAL
  accounts — the account cannot log in until verification completes (return
  403 with a "verify your email" error code the frontend can render
  specially) until `emailVerified=true`. Google OAuth accounts skip this
  entirely (always pre-verified, since Google has already verified the
  email). (The spec's Users section previously contained a dangling
  bracketed placeholder — "[the gated actions defined in Acceptance
  Criteria]" — next to this requirement; that was confirmed dead leftover
  text, not a reference to real content, and has been removed from spec.md.
  The substantive decision itself — gate login only, nothing else — was
  already correct in this design and is unchanged; see spec.md Resolved
  Questions item 4.)

## 7. Flags raised in the prior design pass — now resolved

The first design pass raised three items against the spec as it was then
written. The user has since confirmed resolutions for all three (see
spec.md, Resolved Questions item 4), and this design has been updated
throughout (§1.1, §1.3, §4, §6c) to match. Recorded here for traceability,
not as open flags:

1. **Group location field.** Previously flagged: the spec required
   distance-based group recommendations but gave `Group` no location field
   (only `Activity` had one explicitly), leaving "distance from what" of
   the recommendation calculation undefined. **Resolution:** a group's
   location is captured automatically from the creating user's own location
   (device geolocation, same mechanism used for recommendations elsewhere)
   at the moment of group creation, stored as an immutable snapshot with no
   admin edit/refresh path. This was option (c) of the three options this
   design previously posed (required manual field / optional manual field /
   derived-from-creator), not the optional-manual-field default this design
   had assumed pending confirmation. See §1.3 and §4 for the updated data
   model and request shape.

2. **Dangling "[the gated actions defined in Acceptance Criteria]"
   bracket.** Previously flagged: the Users section's email-verification
   requirement referenced a list of gated actions that was never actually
   enumerated anywhere in Acceptance Criteria, reading like an unfilled
   placeholder. **Resolution:** confirmed dead leftover text from an
   earlier draft, now removed from spec.md. The substantive design decision
   this design had already made next to it — verification gates login only
   for unverified LOCAL accounts; Google OAuth accounts are verified
   immediately — was already correct and required no change; see §6c.

3. **`accountName` derivation for Google OAuth.** Previously flagged (as a
   lower-stakes, non-blocking note): the spec required a unique, immutable
   account name but had no registration-form step to collect one for the
   Google OAuth path, so this design had assumed auto-derivation from the
   Google profile with a numeric collision-suffix. **Resolution:** there is
   no derivation logic at all, for either signup path — `accountName` is
   simply the user's email address (LOCAL: the email registered with;
   OAuth: the email from the Google profile), and this design now drops the
   separate `accountName` column entirely in favor of using the existing
   `email` column directly as the account identifier (see §1.1 for the
   decision and rejected alternative). Since email uniqueness is already
   required and enforced, this trivially satisfies account-name uniqueness
   with no derivation or suffixing needed.

## 8. Java version note (not a spec issue, an environment note)

`backend/pom.xml` currently pins `<java.version>21</java.version>`, while
the top-level `CLAUDE.md` states the stack is "Java 25, Spring Boot 4.1."
This design doesn't depend on any Java 22+ language feature, so it doesn't
block implementation either way — noted so implementer/leader can decide
whether to bump the pom to match `CLAUDE.md` or update `CLAUDE.md` to match
the pom, as a separate housekeeping concern outside this spec's scope.
