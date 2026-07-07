---
status: verified
owner: analyst
created: 2026-07-07
---

# Try demo account

## Problem

Recruiters and other reviewers of this portfolio project currently cannot see
the authenticated parts of ImIn (dashboard, groups, group chat, activities,
map/routing) without going through registration + email verification, which
is unreasonable friction for someone who just wants to evaluate the app in a
couple of minutes. The team has already agreed (this is not up for
re-litigation in this spec) that the fix is a seeded, shared "demo account"
reachable via a one-click "Try demo account" entry point somewhere in the
logged-out flow. This spec pins down exactly how that account is provisioned,
what it is and isn't allowed to do, and where/how the entry point works.

Because the demo account's credentials are necessarily public (or at least
guessable/discoverable) and every visitor authenticates as the *same*
underlying row, this spec also has to define a proportionate abuse-mitigation
strategy so one visitor's session can't leave the account (and therefore
every subsequent visitor's first impression) broken.

## Requirements

### 1. Provisioning (seeding)

- A fixed demo user must exist as a real row in the `users` table — same
  `User` entity as any real account (`backend/src/main/java/com/imin/backend/user/User.java`),
  not a special-cased in-memory user.
- Provisioning is idempotent and runs automatically at backend startup,
  following the codebase's existing seeding convention: an
  `ApplicationRunner`-style component analogous to
  `com.imin.backend.category.CategorySeeder` — check-then-insert-if-absent,
  no Flyway. (This backend has no Flyway dependency at all; `spring.jpa.hibernate.ddl-auto: update`
  is the only schema-management mechanism in use — see `backend/pom.xml` and
  `backend/src/main/resources/application.yml`. Introducing Flyway solely for
  this one seed row would be inconsistent with how every other piece of seed
  data (`GroupCategory`) is already handled in this codebase, so this spec
  requires reusing that existing pattern rather than introducing a new one.)
- On first startup (no existing row with the demo account's fixed email),
  exactly one `User` row is created with:
  - `provider = LOCAL`
  - `emailVerified = true` at creation time (this is the one field that
    matters most: `AuthService.login` returns 403 "email not verified" for any
    unverified `LOCAL` account, and the demo account must never hit that path)
  - a non-null `passwordHash` matching a fixed, known demo password
  - a `displayName` that clearly identifies it as a demo persona (e.g. "Demo
    User"), so it's never confused with a real user in group member lists,
    chat, etc.
  - a new boolean flag on `User` marking it as the demo account (referred to
    below as "the demo flag"; exact field/column name is the architect's
    call), set `true`
- On every subsequent startup, the seeder must not create a second row and
  must not overwrite/reset any field of the already-seeded demo user (matches
  `CategorySeeder`'s existing "no destructive reset of an already-seeded
  row" behavior) — so that manual DB fixes (e.g. after some drift) aren't
  silently undone on the next deploy.
- The fixed demo email/password are not treated as a secret requiring
  rotation-grade handling (they are, by design, public), but should be
  configurable (with a working default) the same way `JWT_SECRET` and
  `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` already are in `application.yml`,
  rather than being a bare string literal buried in the seeder — this is a
  code-hygiene requirement, not a security one.

### 2. Restrictions on the demo account (abuse mitigation)

Because many concurrent visitors authenticate as the same underlying `User`
row (each gets their own JWT, but all mutate the same rows), the demo account
must be barred from actions that would destroy or disrupt what the *next*
visitor sees. The bar is "prevent breakage/griefing of the shared demo
state," not "prevent all writes" — creating new content (groups, chat
messages, activities, friend/block edges) is allowed and expected, since
demonstrating those flows is the entire point of the account existing.

The demo flag on `User` must be checked, and the action rejected with
`403 Forbidden`, in the following existing service methods (all in
`backend/src/main/java/com/imin/backend/`):

- `group/GroupService.java`
  - `deleteGroup` — blocked unconditionally, even for a group the demo
    account created/admins.
  - `updateGroup` — blocked (no renaming/redescribing any group).
  - `leaveGroup` — blocked unconditionally (prevents the demo account ending
    up in zero groups, which would leave the next visitor's dashboard empty).
  - `kickMember`, `banMember`, `unbanMember` — blocked.
  - `addCategory`, `removeCategory` — blocked.
- `chat/GroupChatService.java`
  - `deleteMessage` — blocked, including deleting the demo account's own
    message.
- `activity/ActivityService.java`
  - `updateActivity`, `deleteActivity` — blocked, including an activity the
    demo account owns.
- `user/UserService.java`
  - `updateProfile` — blocked (display name/bio are locked; a mid-demo
    identity change would confuse every other concurrent visitor who sees
    that name in shared groups/chats).

Explicitly **allowed** (unchanged from a normal user, no demo-flag check):
`GroupService.createGroup`, `joinGroup`, `searchGroups`, `getGroup`,
`listMembers`, `recommendGroups`; `GroupChatService.postMessage`,
`getMessages`; `ActivityService.createActivity`, `listActivities`,
`getActivity`; all of `social/SocialService.java`
(`addFriend`/`removeFriend`/`blockUser`/`unblockUser`/`listFriends`/`listBlocks`);
all of `chat/DirectChatService.java`
(`sendMessage`/`getMessages`/`listThreads`).

There is currently no email/password-change endpoint anywhere in the
codebase (`UserService`/`UserController` only expose display name + bio via
`UpdateProfileRequest`). Nothing further needs blocking today, but if such an
endpoint is added later it must be added to this blocked list — noting this
so it isn't missed.

**Reset strategy: none.** Given the blocks above, the demo account can never
end up in a broken/empty state (it can't leave/delete its way to nothing),
so there is no "account is unusable for the next visitor" failure mode to
guard against with a scheduled reset job. What it *can* do is slowly
accumulate created content (extra groups, chat messages, activities,
friend/block edges) over time, since none of that is blocked. For a
portfolio project this is accepted as a documented, low-severity residual
risk rather than something this spec builds automation for — see Out of
scope.

### 3. Entry point placement

A "Try demo account" control appears in **both**
`frontend/src/routes/LoginPage.tsx` and `frontend/src/routes/LandingPage.tsx`,
visible only in their existing logged-out states (neither page is reachable
once authenticated). Both pages are cheap to add a single button/link to and
are the two places a cold, unregistered visitor actually lands, so covering
both is low-cost and maximizes the chance a visitor notices it.

### 4. Auto-login vs pre-fill

Clicking "Try demo account" **auto-authenticates** the visitor immediately —
it does not merely pre-fill the LoginPage's email/password fields for the
visitor to submit themselves. A portfolio reviewer should not have to think
about or even see a login form; one click should land them on the
authenticated dashboard (`navigate('/', { replace: true })`, the same
post-login destination `LoginPage.tsx` already uses on success).

Consequently, **no demo credential (email or password) is displayed as
visible page text anywhere** on LandingPage or LoginPage — there is no user
action that requires the visitor to read or type a credential, so showing
one would only add clutter. (The concrete mechanism the frontend uses to
obtain a valid JWT for the demo account without exposing a credential in the
UI — e.g. a dedicated no-password demo-login endpoint vs. reusing the
standard login endpoint with a frontend-known constant — is the architect's
call; this spec only fixes the observable behavior.)

If demo authentication fails for any reason (backend unreachable, unexpected
error), the visitor must see an inline error state on whichever page they
clicked from — not a silent no-op and not an unhandled crash.

### 5. Security / no privilege escalation

- The JWT issued for the demo account has the same claim shape as any normal
  user's JWT (`uid`, `name` — see `AuthService.issueToken`); no new elevated
  role/scope claim is introduced, and no code path grants the demo account
  broader access *because* it's the demo account (the demo flag is only ever
  used to narrow what it can do, never to widen it).
- The demo account is subject to the same rules as any other account:
  a group admin can ban/kick it like any member, another user can block it
  and thereby prevent it from DMing them, etc.
- The demo account sees only what an ordinary member/participant could see
  through existing authorization (its own groups' member lists, its own DM
  threads, groups it hasn't joined only via the existing public/discovery
  surfaces). It gets no special visibility into any other user's private
  data.

## Out of scope

- Per-visitor isolated/sandboxed demo sessions (each visitor mutating an
  independent copy of demo data). All visitors intentionally share one
  account and its data, per the already-agreed approach.
- Any scheduled or on-demand job to reset/restore demo data to a known-good
  state. Not needed given the restrictions in Requirement 2 prevent the
  account reaching a broken state; slow content accumulation is an accepted
  risk, not something this spec automates away.
- Admin tooling/UI to manually reset or curate demo data.
- Any new rate-limiting or brute-force protection on demo login attempts.
  `POST /api/auth/**` currently has no rate limiting of any kind
  (`SecurityConfig` permits it outright); this spec does not add any, for the
  demo path or otherwise.
- Changes to the registration, email-verification, or Google OAuth2 login
  flows for real users.
- A visible demo credential/"copy password" affordance in the UI (explicitly
  rejected — see Requirement 4's auto-login decision).
- Blocking demo-account content creation volume (extra groups/messages/
  activities/friend edges) — allowed, and only a documented residual risk,
  not an enforced limit.

## Acceptance criteria

Seeding:
- [ ] On a cold backend startup with no existing demo user row, exactly one
      `User` row is created matching Requirement 1 (LOCAL provider,
      `emailVerified = true`, non-null password hash, identifying display
      name, demo flag `true`).
- [ ] Restarting the backend again does not create a second demo user row and
      does not modify any field of the existing one.
- [ ] Logging in with the demo account's fixed credentials never returns the
      403 "email not verified" response that `AuthService.login` returns for
      unverified `LOCAL` accounts.

Entry point / auto-login:
- [ ] A "Try demo account" control is visible on `LoginPage` and on
      `LandingPage`, in their logged-out states only.
- [ ] Clicking it results in an authenticated session (valid stored JWT,
      profile loaded) and navigation to `/`, without the visitor typing,
      viewing, or copying any credential.
- [ ] No demo email or password string appears as rendered page text on
      either page.
- [ ] A failure during demo authentication surfaces an inline error on the
      page the visitor clicked from, rather than a silent no-op or crash.

Restriction enforcement (as caller = demo account unless noted):
- [ ] `GroupService.deleteGroup` → 403, even when the demo account is the
      group's sole admin.
- [ ] `GroupService.updateGroup` → 403.
- [ ] `GroupService.leaveGroup` → 403.
- [ ] `GroupService.kickMember` → 403.
- [ ] `GroupService.banMember` → 403.
- [ ] `GroupService.unbanMember` → 403.
- [ ] `GroupService.addCategory` → 403.
- [ ] `GroupService.removeCategory` → 403.
- [ ] `GroupChatService.deleteMessage` → 403, including for the demo
      account's own message.
- [ ] `ActivityService.updateActivity` → 403, including for an activity the
      demo account owns.
- [ ] `ActivityService.deleteActivity` → 403, including for an activity the
      demo account owns.
- [ ] `UserService.updateProfile` → 403.
- [ ] Each of the following still succeeds unchanged for the demo account:
      `GroupService.createGroup`, `joinGroup`, `searchGroups`, `getGroup`,
      `listMembers`, `recommendGroups`; `GroupChatService.postMessage`,
      `getMessages`; `ActivityService.createActivity`, `listActivities`,
      `getActivity`; `SocialService.addFriend`, `removeFriend`, `blockUser`,
      `unblockUser`, `listFriends`, `listBlocks`; `DirectChatService.sendMessage`,
      `getMessages`, `listThreads`.

Security:
- [ ] The demo account's issued JWT contains the same claim set as a normal
      user's JWT — no additional role/scope claim.
- [ ] A group admin can still ban/kick the demo account like any other
      member; another user blocking the demo account still prevents it from
      DMing them.
- [ ] No endpoint returns the demo account data beyond what its own
      memberships/threads/participation already entitle it to under existing
      authorization checks (no new privileged read path is introduced).

## Design notes

Recorded inline here (no separate `design.md` — this feature is small enough
that splitting the design out would just add indirection). Four decisions
below, then file/module layout, then the pieces that were already read and
require no change.

### 1. Demo flag + seeder

- **New `User` field**: `private boolean demoAccount = false;` added to
  `backend/src/main/java/com/imin/backend/user/User.java`, right after
  `emailVerified`, with `@Column(nullable = false)` — same shape as
  `emailVerified` (primitive boolean, `nullable = false`, Java-side default).
  Lombok generates `isDemoAccount()` / `setDemoAccount(boolean)`. This
  mirrors `emailVerified` exactly, including relying on the same
  `ddl-auto: update` behavior that already successfully added
  `emailVerified` as a non-null boolean column to a live table — no new risk
  class is introduced here, it's the same mechanism this codebase already
  depends on. Existing rows (and any newly-registered real user) get
  `false` from the column default; only the seeded row is ever `true`.
- **New seeder**: `com.imin.backend.user.DemoUserSeeder implements
  ApplicationRunner`, co-located in the `user` package next to `User` /
  `UserRepository` — same relationship `CategorySeeder` has to
  `GroupCategory`/`GroupCategoryRepository` in the `category` package (a
  seeder lives beside the entity it seeds, not in some generic `seed`
  package). Idempotency check reuses `UserRepository.existsByEmail(...)`
  (already exists, used by `AuthService.register`) — no new repository
  method needed for the check itself:

  ```java
  @Component
  @RequiredArgsConstructor
  public class DemoUserSeeder implements ApplicationRunner {

      private final UserRepository userRepository;
      private final PasswordEncoder passwordEncoder;

      @Value("${demo.account.email}")
      private String demoEmail;
      @Value("${demo.account.password}")
      private String demoPassword;
      @Value("${demo.account.display-name}")
      private String demoDisplayName;

      @Override
      public void run(ApplicationArguments args) {
          if (userRepository.existsByEmail(demoEmail)) {
              return; // already seeded — never overwrite (matches CategorySeeder)
          }
          User demoUser = new User();
          demoUser.setEmail(demoEmail);
          demoUser.setPasswordHash(passwordEncoder.encode(demoPassword));
          demoUser.setDisplayName(demoDisplayName);
          demoUser.setProvider(AuthProvider.LOCAL);
          demoUser.setEmailVerified(true);
          demoUser.setDemoAccount(true);
          userRepository.save(demoUser);
      }
  }
  ```

  `PasswordEncoder` is already a bean (used by `AuthService`), so this is a
  constructor-injected dependency like any other — no new bean needed.
  Exactly one row is ever created because the check is by email, and email
  has a unique constraint (`User.email` is `@Column(unique = true)`).

### 2. Demo credentials config

No `@ConfigurationProperties` class — grepping the codebase confirms `@Value`
on individual fields (see `JwtConfig`, `AuthService.frontendUrl`) is the
*only* externalized-config pattern in use here; introducing
`@ConfigurationProperties` for three strings would be a new pattern for no
real benefit. Three `@Value`-injected fields on `DemoUserSeeder` (above),
backed by new `application.yml` keys with working dev defaults, exactly like
`jwt.secret` / `google.client-id`:

`backend/src/main/resources/application.yml` — new top-level block:

```yaml
demo:
  account:
    email: ${DEMO_ACCOUNT_EMAIL:demo@imin.app}
    password: ${DEMO_ACCOUNT_PASSWORD:ImInDemo2026!}
    display-name: ${DEMO_ACCOUNT_DISPLAY_NAME:Demo User}
```

`backend/.env.example` — new documented block (not secret, but documented
the same way every other env var is, per Requirement 1's code-hygiene ask):

```
# Shared "Try demo account" login (public by design — see
# specs/try-demo-account/spec.md). Override only if you want a different
# fixed demo persona; the defaults work out of the box.
DEMO_ACCOUNT_EMAIL=demo@imin.app
DEMO_ACCOUNT_PASSWORD=ImInDemo2026!
DEMO_ACCOUNT_DISPLAY_NAME=Demo User
```

### 3. Restriction enforcement mechanism

**Decision: a private `assertNotDemoAccount(User user)` helper method,
duplicated locally in each of the four affected service classes
(`GroupService`, `GroupChatService`, `ActivityService`, `UserService`),
called as the first line after the caller `User` is resolved in every
blocked method.** No new exception type, no AOP/annotation mechanism.

Rationale:
- `ResponseStatusException(HttpStatus.FORBIDDEN, message)` is already this
  codebase's universal 403 mechanism (used throughout `GroupService`,
  `GroupChatService`, `ActivityService`, `UserService`, and explicitly
  special-cased in `ApiExceptionHandler`'s javadoc as *the* cross-service
  error-throwing convention). Reusing it means zero new infrastructure.
- On duplication vs. a shared utility: this codebase already has a directly
  analogous precedent for *not* extracting a shared helper — every one of
  these four services independently defines its own private
  `findUserByEmail(String email)` with an identical body, rather than a
  shared `UserLookup` utility. A one-line demo-flag check is even smaller
  than that duplicated method, so following the same local-private-helper
  convention is more consistent with the codebase's existing style than
  introducing a new shared class (e.g. `DemoAccountGuard`) would be — that
  would be the first cross-service utility of its kind here, for a check
  this small.
- Checking first, before any group/message/activity lookup or other
  authorization check (`requireAdmin`, `requireOwnerOrAdmin`, etc.), makes
  the block unconditional and deterministic per Requirement 2's wording
  ("blocked unconditionally, even for a group the demo account created/
  admins") — the demo account gets the same specific 403 message
  regardless of whether it would otherwise have passed or failed the
  underlying authorization check, and it's a cheap short-circuit (skips
  unnecessary lookups for an action that's blocked either way).

Shape (identical in each of the four classes, only the message body is
shared verbatim for consistency):

```java
private void assertNotDemoAccount(User user) {
    if (user.isDemoAccount()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This action is disabled for the shared demo account");
    }
}
```

Call sites (caller resolution unchanged, one new line each):

- `GroupService`: `deleteGroup`, `updateGroup`, `leaveGroup`, `kickMember`,
  `banMember`, `unbanMember`, `addCategory`, `removeCategory` — call right
  after `User caller = findUserByEmail(callerEmail);`.
- `GroupChatService`: `deleteMessage` — same, right after `findUserByEmail`.
- `ActivityService`: `updateActivity`, `deleteActivity` — same.
- `UserService`: `updateProfile` — same.

Not touched: `createGroup`, `joinGroup`, `searchGroups`, `getGroup`,
`listMembers`, `recommendGroups`, `postMessage`, `getMessages` (both chat
services), `createActivity`, `listActivities`, `getActivity`, all of
`SocialService`, all of `DirectChatService` — per Requirement 2's explicit
allow-list, these get no new code at all.

### 4. Auto-login mechanism

**Decision: option (a) — a dedicated, passwordless `POST
/api/auth/demo-login` endpoint**, not the frontend reusing `/api/auth/login`
with a hardcoded credential.

Rationale:
- It avoids ever putting the plaintext demo password in frontend source or
  the shipped JS bundle. The spec is explicit that secrecy of the
  credential itself isn't the goal (it's "public by design"), but there is
  still a real, free difference between "the frontend bundle contains a
  parseable `email`/`password` pair matching a real login form's request
  shape" and "the frontend calls an endpoint that requires no credential at
  all" — the latter is strictly less for a casual scraper to find and reuse
  against other accounts (moot here since the password isn't reused
  anywhere else, but it's free to get right).
- `/api/auth/**` is already `permitAll()` in `SecurityConfig`
  (`.requestMatchers("/api/auth/**", ...).permitAll()`), so the new
  endpoint needs **zero** `SecurityConfig` changes — it falls under the
  existing wildcard.
- It reuses `AuthService`'s existing private `issueToken(User user)` method
  verbatim, so the issued JWT has exactly the same claim shape (`uid`,
  `name`, same expiry) as a normal login — satisfies Requirement 5 by
  construction rather than by new code that has to be independently
  verified against it.
- It looks up the demo user **by the demo flag**, not by the configured
  email: `UserRepository.findFirstByDemoAccountTrue()` (new derived-query
  method, one line). This is a deliberate small defense-in-depth choice —
  the authoritative "is this the demo account" signal is the flag on the
  row, not whatever the current `demo.account.email` config value happens
  to be, so the login path and the restriction-enforcement path (§3) key
  off the exact same field. If misconfigured/missing (should not happen in
  practice since the seeder runs on every startup), it throws
  `ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Demo account
  is not provisioned")` — a real server-side bug, not a client error, so
  500 is the correct/honest status rather than inventing a 404/409 for a
  case that acceptance criteria don't otherwise test.

New/changed backend pieces:

- `UserRepository`: add `Optional<User> findFirstByDemoAccountTrue();`
- `AuthService`: add
  ```java
  public AuthResponse demoLogin() {
      User user = userRepository.findFirstByDemoAccountTrue()
              .orElseThrow(() -> new ResponseStatusException(
                      HttpStatus.INTERNAL_SERVER_ERROR, "Demo account is not provisioned"));
      return issueToken(user);
  }
  ```
- `AuthController`: add
  ```java
  @PostMapping("/demo-login")
  public ResponseEntity<AuthResponse> demoLogin() {
      return ResponseEntity.ok(authService.demoLogin());
  }
  ```
  No request body — there is nothing for the client to submit.

Alternative considered and rejected: reusing `/api/auth/login` with the
frontend importing a `DEMO_EMAIL`/`DEMO_PASSWORD` constant and calling the
existing `login()` from `AuthContext`. Rejected because it (a) puts the
credential in the bundle for no benefit given a passwordless path is just as
easy to build, and (b) makes the frontend implicitly depend on the demo
password matching whatever the backend is configured with at build time —
fragile if `DEMO_ACCOUNT_PASSWORD` is ever overridden per-environment,
whereas the dedicated endpoint has no such coupling at all.

### 5. Frontend integration

`frontend/src/lib/apiClient.ts` — new thin wrapper next to `login`/
`register`, reusing the existing `AuthResponse` type (identical shape,
no new type needed):

```ts
export function demoLogin(): Promise<AuthResponse> {
  return apiFetch<AuthResponse>('/api/auth/demo-login', { method: 'POST', skipAuth: true })
}
```

`frontend/src/context/AuthContext.tsx` — new context function
`loginAsDemo`, same shape as `login`/`loginWithToken` (store token, then
`loadProfile()`), added to `AuthContextValue` and the provider's `value`:

```ts
const loginAsDemo = useCallback(async () => {
  const response = await demoLogin()
  setStoredToken(response.token)
  await loadProfile()
}, [loadProfile])
```

This is the one genuinely shared piece both pages need, so it belongs in
`AuthContext` (not duplicated per-page) — consistent with `login` and
`loginWithToken` already living there for the same reason.

`frontend/src/routes/LoginPage.tsx` — add a "Try demo account" button
below the existing "Sign in with Google" link, wired to a new handler that
reuses the form's *existing* `isSubmitting`/`error` state (no new state
needed — one loading/error surface per page, as the spec requires):

```ts
async function handleDemoLogin() {
  setError(null)
  setUnverified(false)
  setIsSubmitting(true)
  try {
    await loginAsDemo()
    navigate('/', { replace: true })
  } catch (err) {
    setError(err instanceof ApiError ? err.message : 'Could not start the demo session. Please try again.')
  } finally {
    setIsSubmitting(false)
  }
}
```

`frontend/src/routes/LandingPage.tsx` — currently a fully static component
with no hooks/state at all. Needs `useState` (local `isSubmitting`/`error`,
scoped to this page only — do not lift into a shared/global state, this
page has never needed shared state and shouldn't start now), `useNavigate`,
and `useAuth()` added, with a third button (not a `<Link>`, since it
triggers an async action rather than navigating) placed in the header next
to "Get ImIn"/"Log in", plus an inline error line rendered under the button
group on failure. Same handler shape as `LoginPage`'s, minus the
`unverified` special-case (irrelevant here — demo login never hits that
path).

Both buttons render only the label "Try demo account" — no interpolated
email/password anywhere in JSX, satisfying Requirement 4/Acceptance
criterion "no demo email or password string appears as rendered page text."

### File/module layout summary

New files:
- `backend/src/main/java/com/imin/backend/user/DemoUserSeeder.java`

Changed files:
- `backend/src/main/java/com/imin/backend/user/User.java` — add `demoAccount` field.
- `backend/src/main/java/com/imin/backend/user/UserRepository.java` — add `findFirstByDemoAccountTrue()`.
- `backend/src/main/java/com/imin/backend/auth/AuthService.java` — add `demoLogin()`.
- `backend/src/main/java/com/imin/backend/auth/AuthController.java` — add `POST /demo-login`.
- `backend/src/main/resources/application.yml` — add `demo.account.*` keys.
- `backend/.env.example` — document `DEMO_ACCOUNT_*` vars.
- `backend/src/main/java/com/imin/backend/group/GroupService.java` — add `assertNotDemoAccount` + 8 call sites.
- `backend/src/main/java/com/imin/backend/chat/GroupChatService.java` — add `assertNotDemoAccount` + 1 call site (`deleteMessage`).
- `backend/src/main/java/com/imin/backend/activity/ActivityService.java` — add `assertNotDemoAccount` + 2 call sites.
- `backend/src/main/java/com/imin/backend/user/UserService.java` — add `assertNotDemoAccount` + 1 call site (`updateProfile`).
- `frontend/src/lib/apiClient.ts` — add `demoLogin()`.
- `frontend/src/context/AuthContext.tsx` — add `loginAsDemo`.
- `frontend/src/routes/LoginPage.tsx` — add button + handler.
- `frontend/src/routes/LandingPage.tsx` — add hooks, button + handler, error line.

No changes needed to: `SecurityConfig` (new endpoint already covered by the
existing `/api/auth/**` permitAll matcher), `ApiExceptionHandler` (existing
`ResponseStatusException` handling covers the new 403s and the 500 fallback
without modification), `JwtService`/`JwtConfig` (token issuance path is
reused unchanged via `issueToken`), `SocialService`/`DirectChatService`
(explicitly out of scope — no demo check anywhere in either).

### Feasibility check

Nothing in the spec turned out to be infeasible or underspecified once
digging into the actual service/entity code — `ResponseStatusException` +
`HttpStatus.FORBIDDEN` is a clean, already-idiomatic fit for every
Requirement 2 call site, `/api/auth/**`'s existing blanket `permitAll()`
means the new endpoint needs no security-config surgery, and adding a
not-null boolean column via `ddl-auto: update` is not a new risk — it's the
exact same mechanism that already put `emailVerified` into production.
Nothing is being sent back to Analyst.

## Implementation notes

Implemented exactly per the design notes above, no deviations.

**Backend — new files:**
- `backend/src/main/java/com/imin/backend/user/DemoUserSeeder.java` — `ApplicationRunner`,
  idempotent via `userRepository.existsByEmail(demoEmail)`, mirrors `CategorySeeder` exactly.

**Backend — changed files:**
- `backend/src/main/java/com/imin/backend/user/User.java` — added
  `@Column(nullable = false) private boolean demoAccount = false;` right after `emailVerified`.
- `backend/src/main/java/com/imin/backend/user/UserRepository.java` — added
  `Optional<User> findFirstByDemoAccountTrue();`.
- `backend/src/main/java/com/imin/backend/auth/AuthService.java` — added `demoLogin()`, looking
  up by the demo flag and reusing the existing private `issueToken(User)` verbatim.
- `backend/src/main/java/com/imin/backend/auth/AuthController.java` — added
  `POST /api/auth/demo-login` (no request body), delegating to `authService.demoLogin()`.
- `backend/src/main/resources/application.yml` — added `demo.account.email/password/display-name`
  keys with the dev defaults from the design notes, following the `jwt.secret` /
  `google.client-id` env-override pattern.
- `backend/.env.example` — documented `DEMO_ACCOUNT_EMAIL/PASSWORD/DISPLAY_NAME`, matching the
  existing `JWT_SECRET`/`GOOGLE_CLIENT_ID` documentation style.
- `backend/src/main/java/com/imin/backend/group/GroupService.java` — added a private
  `assertNotDemoAccount(User)` helper (same body/message as specified) and one call each,
  immediately after `User caller = findUserByEmail(...)`, in `deleteGroup`, `updateGroup`,
  `leaveGroup`, `kickMember`, `banMember`, `unbanMember`, `addCategory`, `removeCategory`.
- `backend/src/main/java/com/imin/backend/chat/GroupChatService.java` — added the same helper
  and one call site in `deleteMessage`.
- `backend/src/main/java/com/imin/backend/activity/ActivityService.java` — added the same helper
  and one call site each in `updateActivity` and `deleteActivity`.
- `backend/src/main/java/com/imin/backend/user/UserService.java` — added the same helper and one
  call site in `updateProfile` (this class's existing lookup helper is named `findByEmail`, not
  `findUserByEmail` as in the other three services — call site placement follows the same
  "right after the caller `User` is resolved" rule, just against that class's own naming).
- No changes were needed to `SecurityConfig` — confirmed `/api/auth/**` is already
  `permitAll()`, so `POST /api/auth/demo-login` needs no new matcher.

**Frontend — changed files:**
- `frontend/src/lib/apiClient.ts` — added `demoLogin()`, a thin `POST /api/auth/demo-login`
  wrapper (`skipAuth: true`, no body) reusing the existing `AuthResponse` type, placed next to
  `login`/`register`.
- `frontend/src/context/AuthContext.tsx` — added `loginAsDemo` (store token, then
  `loadProfile()`, same shape as `login`), exposed on `AuthContextValue` and the provider's
  `value`.
- `frontend/src/routes/LoginPage.tsx` — added a `handleDemoLogin` handler reusing the form's
  existing `isSubmitting`/`error` state (no new state), and a "Try demo account" button below the
  "Sign in with Google" link, styled identically to that link (same border-button classes).
- `frontend/src/routes/LandingPage.tsx` — added `useState`-backed local `isSubmitting`/`error`
  (scoped to this page only, not lifted into shared state), `useNavigate`, and `useAuth()`; added
  a third button (not a `<Link>`) next to "Get ImIn"/"Log in" in the header, styled to match the
  "Log in" button's outline style, plus an inline error line (`text-error`, the same token
  `LoginPage` uses for its generic error message) rendered under the button group on failure.

Neither page renders any interpolated email/password string — both buttons render only the
static label "Try demo account".

**Verification run by implementer (build/compile sanity only — exhaustive test coverage is the
tester's job):**
- `backend`: `./mvnw -o compile` → `BUILD SUCCESS`.
- `backend`: `./mvnw -o test` → `Tests run: 179, Failures: 0, Errors: 0, Skipped: 0` — all
  pre-existing tests still pass unchanged (no test yet exercises the new demo-account behavior;
  that's left for the tester per the pipeline's division of labor).
- `frontend`: `npm run build` (`tsc -b && vite build`) → succeeded with no type errors.

## Verification

**Backend — automated, real tests run against a real (H2 in-memory) repository stack, following this
codebase's existing test conventions exactly (package-beside-service placement, `@SpringBootTest` +
`@ActiveProfiles("test")` + `@Transactional`, MockMvc for HTTP-level flows).**

New/changed test files:
- `backend/src/test/java/com/imin/backend/user/DemoUserSeederTest.java` (new) — Requirement 1 /
  "Seeding" acceptance criteria. Invokes `DemoUserSeeder.run(...)` directly (deterministic — avoids
  depending on real application-startup ordering relative to other test classes sharing this test
  JVM's single named H2 instance, some of which wipe all users outside a transaction).
  - `coldStartupSeedsExactlyOneCorrectlyShapedDemoUser` — asserts exactly one row, `provider = LOCAL`,
    `emailVerified = true`, `demoAccount = true`, non-null `passwordHash` matching the configured
    default password, `displayName` matching config.
  - `rerunningSeederDoesNotCreateASecondRowOrResetAnyField` — seeds, manually mutates `displayName`
    (simulating a manual DB fix), reruns the seeder, asserts still exactly one row with the same id
    and the manual edit intact (not reset).
  - `loggingInWithDemoCredentialsNeverHitsEmailNotVerified403` — calls `AuthService.login` with the
    demo credentials and asserts a normal token response, not the 403 branch.
- `backend/src/test/java/com/imin/backend/auth/DemoLoginFlowTest.java` (new) — full HTTP flow via
  MockMvc, mirroring `AuthFlowIntegrationTest`'s pattern. `demoLoginIssuesUsableJwtWithNoRequestBodyOrCredentials`
  (POST with no body → 200 + token; token then authenticates `GET /api/users/me` as the demo user);
  `demoLoginNeverReturnsEmailNotVerified403ViaHttp`; `demoJwtHasSameClaimShapeAsNormalUserJwtNoElevatedClaims`
  (decodes both JWTs' payloads and asserts identical claim-name sets — `iss/iat/exp/sub/uid/name`,
  nothing extra).
- `backend/src/test/java/com/imin/backend/group/GroupServiceTest.java` (extended) — all 8
  `GroupService` blocked call sites, each asserting 403 and that the attempted mutation did not
  happen: `demoAccountCannotDeleteGroupEvenAsSoleAdmin`, `demoAccountCannotUpdateGroup`,
  `demoAccountCannotLeaveGroup`, `demoAccountCannotKickMember`, `demoAccountCannotBanMember`,
  `demoAccountCannotUnbanMember` (demo promoted to co-admin first, to prove the block isn't just a
  side effect of lacking admin rights), `demoAccountCannotAddCategory`, `demoAccountCannotRemoveCategory`.
  Plus `demoAccountCanStillCreateAndJoinGroupsNormally` (allowed spot-check) and
  `anotherAdminCanStillKickOrBanTheDemoAccountAsATarget` (security edge case: a non-demo admin can
  still kick/ban the demo account as a *target*).
- `backend/src/test/java/com/imin/backend/chat/GroupChatServiceTest.java` (extended) —
  `demoAccountCannotDeleteAnyMessageIncludingItsOwn` (the one `GroupChatService` blocked call site)
  and `demoAccountCanStillPostAndReadMessagesNormally` (allowed spot-check).
- `backend/src/test/java/com/imin/backend/activity/ActivityServiceTest.java` (extended) —
  `demoAccountCannotUpdateOrDeleteAnActivityItOwns` (both `ActivityService` blocked call sites) and
  `demoAccountCanStillCreateAndListActivitiesNormally` (allowed spot-check).
- `backend/src/test/java/com/imin/backend/user/UserServiceTest.java` (extended) —
  `demoAccountCannotUpdateItsProfile` (the one `UserService` blocked call site).
- `backend/src/test/java/com/imin/backend/social/SocialServiceTest.java` (extended) —
  `demoAccountCanStillAddFriendBlockAndUnblockNormally` (allowed spot-check across all of
  `SocialService`) and `anotherUserCanStillBlockTheDemoAccountAsATarget` (security edge case).
- `backend/src/test/java/com/imin/backend/chat/DirectChatServiceTest.java` (extended) —
  `demoAccountCanStillSendAndReadDirectMessagesNormally` (allowed spot-check) and
  `anotherUserBlockingTheDemoAccountStillPreventsItFromDmingThem` (security edge case: blocking the
  demo account as a target still gates its outbound DMs, per Requirement 5).

**Test run result**: `./mvnw -o test` from `backend/` → `Tests run: 204, Failures: 0, Errors: 0,
Skipped: 0` (179 pre-existing + 25 new, all passing). Re-ran twice to confirm no ordering-related
flakiness given several test classes share one named H2 in-memory instance across the whole test
JVM — both runs identical (204/0/0/0).

**Acceptance criteria coverage:**
- Seeding (3/3): all verified by `DemoUserSeederTest`.
- Entry point / auto-login (4 criteria): the two backend-observable ones ("clicking it results in an
  authenticated session" at the JWT/API level, and the JWT claim shape) are verified by
  `DemoLoginFlowTest`. The other two ("Try demo account" control visible on both pages in their
  logged-out states only; no demo email/password string rendered as page text; inline error on
  failure) are **frontend-only and could not be automated** — `frontend/package.json` has no test
  runner configured (no vitest/jest, no `test` script), matching the precedent already recorded in
  `specs/landing-page/spec.md`'s Verification section. Verified instead by direct code review of
  `LoginPage.tsx`, `LandingPage.tsx`, `AuthContext.tsx`, and `apiClient.ts`: both pages render a
  button whose only text is the literal string `"Try demo account"` (no interpolated credential
  anywhere in JSX), both call `loginAsDemo()` → `navigate('/', { replace: true })` on success, and
  both catch failures into a local `error` state rendered inline on the same page (no silent no-op,
  no unhandled crash) — this is a code-review-level check, not an executed test, and is flagged as
  such.
- Restriction enforcement (all 12 blocked call sites + allowed spot-checks): fully verified by the
  extended service test files above — every one of the 12 required 403s has a dedicated test, and
  `createGroup`, `joinGroup`, `postMessage`, `getMessages`, `createActivity`, `listActivities`, all of
  `SocialService`, and `DirectChatService.sendMessage`/`getMessages`/`listThreads` are each spot-checked
  as still working normally for the demo account. `searchGroups`, `getGroup`, `listMembers`,
  `recommendGroups`, and `ActivityService.getActivity` are not separately spot-checked with a demo
  actor (no demo-specific code path touches them at all — they're plain, unmodified read paths
  already covered by this codebase's pre-existing tests with normal users), consistent with the
  task's steer to spot-check rather than exhaustively re-cover every allowed method.
- Security (3 criteria): JWT claim-shape parity verified by `DemoLoginFlowTest`
  (`demoJwtHasSameClaimShapeAsNormalUserJwtNoElevatedClaims`). The kick/ban/block-as-target edge case
  verified by `GroupServiceTest.anotherAdminCanStillKickOrBanTheDemoAccountAsATarget`,
  `SocialServiceTest.anotherUserCanStillBlockTheDemoAccountAsATarget`, and
  `DirectChatServiceTest.anotherUserBlockingTheDemoAccountStillPreventsItFromDmingThem`. "No endpoint
  returns the demo account data beyond what its own memberships/threads/participation already
  entitle it to" was **not independently re-verified** here beyond noting that no demo-flag branch
  was added to any read/list/get path in the Implementation notes' file list (`GroupService`'s
  `getGroup`/`listMembers`/`searchGroups`/`recommendGroups`, `GroupChatService.getMessages`,
  `ActivityService.getActivity`/`listActivities`, `SocialService.listFriends`/`listBlocks`,
  `DirectChatService.getMessages`/`listThreads` are all unmodified per the diff) — this is a
  code-inspection conclusion, not a dedicated new test proving no *other* privileged read path exists
  anywhere in the codebase.

All backend acceptance criteria are verified by a passing automated test. Frontend acceptance
criteria are verified by code review only (no test runner available), per the noted precedent — this
is a documented gap, not a silent skip.
