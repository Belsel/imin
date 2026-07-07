---
status: verified
owner: analyst
created: 2026-07-07
---

# Public Landing Page

## Problem

ImIn has no landing/marketing page. In `frontend/src/App.tsx`, `/` is
declared inside `<ProtectedRoute>` (`frontend/src/routes/ProtectedRoute.tsx`),
which unconditionally redirects any unauthenticated visitor to `/login` via
`<Navigate to="/login" replace />`. There is nowhere an unauthenticated
visitor can go to learn what ImIn is — `LoginPage.tsx` shows only the
wordmark "ImIn" and the tagline "Find your people. Get ImIn." above the
login form, with no explanatory copy. Anyone who reaches the site without
already knowing what it does (e.g. from a shared link, a search result, or
just typing the domain) is dropped straight into a login form with no
context. ImIn needs a brief, unauthenticated-facing page at `/` that
explains what the product is and directs visitors to log in or register,
without disrupting the existing behavior for already-authenticated users.

## Requirements

### 1. Routing

- `/` must render differently depending on auth state:
  - An **unauthenticated** visitor hitting `/` sees the new landing page.
  - An **authenticated** user hitting `/` sees the existing `HomePage`
    (dashboard), exactly as today — this must not regress.
- This requires restructuring the `/` route in `frontend/src/App.tsx` so it
  is no longer unconditionally nested under `<ProtectedRoute>` (which
  redirects to `/login` for any logged-out visitor). The `/` route must
  branch on auth state itself: landing page when logged out, `HomePage`
  when logged in.
- While the initial auth/session check is in flight (the same loading
  window `ProtectedRoute` already handles for other routes), `/` may show
  a brief loading state consistent with the existing pattern (see
  `ProtectedRoute.tsx`'s `isLoading` branch) rather than flashing the
  landing page or `HomePage` prematurely.
- All other existing protected routes (`/groups`, `/profile`, `/friends`,
  `/messages`, etc.) keep their current behavior unchanged — this spec
  only changes how `/` resolves.

### 2. Page content

The landing page is a single, brief static page — not a multi-section
marketing site. It must contain:

- The ImIn wordmark and the existing tagline, "Find your people. Get
  ImIn."
- A short explanation (a few sentences or short bullet points) of what
  ImIn is and does, grounded in the actual MVP feature set
  (`specs/mvp/spec.md`): users form and join groups around shared
  activities, discover relevant groups (location- and category-based
  recommendations), coordinate via group chat, and plan activities with
  map-based locations and real turn-by-turn routing.
- Clear calls to action linking into the existing auth routes: a link/
  button to `/login` and a link/button to `/register`.

The page must not perform any new data fetching or backend calls — it is
static marketing copy plus links into existing routes.

### 3. Content accuracy

All copy must be grounded in `specs/mvp/spec.md`'s actual feature set. No
invented or aspirational features may be described — e.g. no mention of
event ticketing, payments, notifications, RSVPs, or any other capability
not present in the MVP spec's Requirements section.

### 4. Visual design

The page must use the existing design token system established in
`frontend/src/index.css` (the palette and font pairing — Syne display /
DM Sans body — already used across the rest of the app), consistent with
the visual language established by `specs/ui-redesign/spec.md`. It must
not default to a generic AI-default landing page layout (e.g. a plain
purple-gradient-on-white hero, or a reflexive three-equal-feature-card
grid chosen without regard to the product). The specific visual treatment
is left to the architect/implementer, informed by the project's
frontend-aesthetics conventions; this spec only requires that the page be
recognizably part of ImIn's existing design system.

## Out of scope

- Any new backend endpoints or API calls.
- Image or illustration assets (the MVP has none; see
  `specs/ui-redesign/spec.md`'s out-of-scope note).
- Analytics or tracking of any kind.
- SEO meta-tag work beyond what is trivial (e.g. no dedicated
  sitemap/robots work, no structured data).
- A multi-page marketing site (no separate pricing, about, blog, or
  careers pages) — this is one page only.
- Changes to `LoginPage`, `RegisterPage`, or any other existing route's
  content or behavior beyond the `/` routing change described above.

## Acceptance criteria

- [ ] An unauthenticated visitor navigating to `/` sees the new landing
      page — not a redirect to `/login`.
- [ ] An authenticated user navigating to `/` sees the existing `HomePage`
      dashboard, exactly as before this change — not the landing page.
- [ ] The landing page displays the ImIn wordmark and the tagline "Find
      your people. Get ImIn."
- [ ] The landing page contains a working link/button to `/login` and a
      working link/button to `/register`, both of which navigate
      correctly when clicked.
- [ ] The landing page's explanatory copy mentions groups, group/activity
      discovery, group chat, and map-based activity planning with
      routing — the core MVP feature set — without inventing any feature
      not present in `specs/mvp/spec.md`.
- [ ] A grep for `lorem` (case-insensitive) across the landing page's
      source returns zero matches, and the copy contains no generic
      placeholder text (e.g. "Feature One", "Lorem ipsum", "Widget").
- [ ] The landing page uses only color, font, and spacing values from the
      existing Tailwind theme tokens in `frontend/src/index.css` — no
      arbitrary one-off values (e.g. `text-[15px]`, `p-[22px]`) and no
      hardcoded hex colors outside the token layer.
- [ ] All other existing routes (`/groups`, `/groups/new`,
      `/groups/:groupId`, `/groups/:groupId/activities/:activityId`,
      `/profile`, `/friends`, `/messages`, `/messages/:userId`,
      `/login`, `/register`, `/verify-email`, `/oauth2/callback`)
      continue to behave exactly as before this change.
- [ ] `frontend` builds and typechecks cleanly (`npm run build` /
      `tsc --noEmit`) with the new page and routing change in place.

## Design notes

(filled in by architect)

## Implementation notes

No architect design doc — skipped per Leader as a small, single-new-component
change following existing conventions. Implemented directly against this
spec, informed by `.claude/skills/frontend-aesthetics/SKILL.md`.

**Routing** (`frontend/src/App.tsx`): Removed `path="/"` from inside the
`<Route element={<ProtectedRoute />}>` block and added a new top-level
`<Route path="/" element={<RootRoute />} />`, declared alongside the other
public routes (`/login`, `/register`, etc.) rather than under
`ProtectedRoute`. `RootRoute` is a small local component in `App.tsx` that
calls `useAuth()` and mirrors `ProtectedRoute.tsx`'s `isLoading` branch
exactly (same "Loading…" markup on `bg-background`) so the initial session
check never flashes the landing page or `HomePage` prematurely; once
loading resolves it renders `<HomePage />` when `user` is set and
`<LandingPage />` otherwise. All other routes (`/groups`, `/groups/new`,
`/groups/:groupId`, `/groups/:groupId/activities/:activityId`, `/profile`,
`/friends`, `/messages`, `/messages/:userId`) are untouched, still nested
under the same `<ProtectedRoute>` element as before.

**New component** (`frontend/src/routes/LandingPage.tsx`): Static,
data-fetch-free component following the existing route-component
conventions (default export, no props). Uses `Link` from `react-router` for
both CTAs (`/register`, `/login`), matching `LoginPage.tsx`'s pattern.

**Content**: Wordmark "ImIn" and the exact tagline "Find your people. Get
ImIn." (copied verbatim from `LoginPage.tsx`) sit in the hero. A one-line
pitch plus a 3-step "How ImIn works" section cover the MVP feature set per
`specs/mvp/spec.md`: (1) "Find your groups" — discovering groups via
location/interest recommendations, or starting one, (2) "Coordinate in the
chat" — each group's own chat, and (3) "Show up with real directions" —
map-pinned activities with turn-by-turn routing. No invented features
(no ticketing/payments/notifications/RSVPs). Grep for `lorem` (case
insensitive) across the new file returns zero matches; no generic
placeholder copy.

**Visual design decisions** (per `frontend-aesthetics` skill process):
- **Page's single job** (stated explicitly before designing): convince a
  cold visitor — arriving via a shared link, search result, or bare domain,
  on mobile or desktop — in a few seconds that ImIn is a real-world
  group/activity finder worth an account, then route them to `/register`
  or `/login`.
- **Layout concept**: two sections only, per the spec's "brief, not a
  multi-section marketing site" constraint — a teal (`bg-primary`) hero
  band with wordmark, tagline, one-line pitch, and both CTAs, followed by
  a quiet `bg-background` section below.
- **Signature element** (the one deliberate risk, per the skill's
  "restraint" principle): the "How ImIn works" section is a vertical
  numbered timeline — three steps connected by a thin `bg-primary/30`
  line running through numbered circles (`bg-primary`/`text-on-primary`)
  — rather than a reflexive three-equal-card grid (explicitly one of the
  "AI-default looks" the skill and this spec call out to avoid). The
  connecting line is a deliberate callback to ImIn's actual differentiator
  — real turn-by-turn routing — and the numbering encodes a true sequence
  (discover → coordinate → go), not decoration (per "Structure is
  information" in the skill). This is a distinct treatment from every
  other signature element already used elsewhere per
  `specs/ui-redesign/spec.md`'s implementation notes (LoginPage's
  full-page teal wrapper, HomePage's eyebrow label, GroupDetailPage's
  category chips), so the landing page doesn't read as a copy-paste of
  any existing screen.
- **Tokens only**: every color, font, and spacing value comes from
  `frontend/src/index.css`'s existing `@theme` tokens
  (`bg-primary`/`bg-background`/`bg-surface`, `text-on-primary`/`text-text`/
  `text-text-muted`, `font-display`/`font-body`) and Tailwind's default
  spacing/type scale — no arbitrary bracket values, no hardcoded hex.
  `--color-accent` was deliberately not used for the CTA buttons: no
  existing component uses it as a solid button background (only as text/
  border, e.g. `GroupDetailPage`'s kick button), and a contrast check
  (white text on `#D97706`) comes out at roughly 3.2:1 — under the 4.5:1
  body-text bar this spec/skill require — so introducing it as a filled
  CTA background would regress on the accessibility principle for no
  visual gain over the existing teal/white pairing already verified
  elsewhere.
- **CTA styling**: primary CTA ("Get ImIn" → `/register`) is a solid
  `bg-surface`/`text-primary` pill (white-on-teal, inverse of the usual
  teal button, high contrast); secondary CTA ("Log in" → `/login`) is an
  outline `border-on-primary/60`/`text-on-primary` pill with a subtle
  hover fill — the same solid/outline primary-vs-secondary pairing already
  used throughout the app (e.g. `HomePage.tsx`'s CTA row), just adapted to
  sit on the teal hero background instead of the page background.
- **Focus states**: both CTA links use the same `focus:outline-none
  focus:ring-2 ... focus:ring-offset-2` pattern already established on
  every other button/link in the codebase (e.g. `LoginPage.tsx`), so they
  get a visible ring rather than relying on (and losing, since a ring
  color/offset must be specified against the teal background) the global
  `@layer base` focus style.
- **Motion**: no JS-driven or decorative animation added; the only
  `transition-colors` uses are on hover states, consistent with the
  existing `motion-safe:hover:*` pattern used elsewhere, so
  `prefers-reduced-motion` (handled globally in `index.css`) applies
  automatically.
- **Responsive**: hero and steps section both use `mx-auto max-w-*` with
  `px-6` and `flex-wrap` on the CTA row, so nothing clips or forces
  horizontal scroll at 375px/768px/1280px.

**Deviations from spec**: None.

**Verification run** (implementer sanity check, not the tester's pass):
`npm run build` (`tsc -b && vite build`) completed with no errors. `npx
oxlint` (project's `npm run lint` proxies to this) reported no new
warnings/errors; the one pre-existing warning
(`react(only-export-components)` in `AuthContext.tsx`) is unrelated to
this change.

## Verification

Tester stage skipped by the Leader: `frontend/package.json` has no test
runner (no vitest/jest), and this is a static, presentational page whose
only logic (auth-branching at `/`) was independently exercised by the
reviewer. Verification performed instead:

- **Reviewer pass**: independently re-read `App.tsx`, `LandingPage.tsx`,
  `ProtectedRoute.tsx`, and `AuthContext.tsx`; independently re-ran
  `npm run build` and `npx oxlint`; cross-checked all landing-page copy
  against `specs/mvp/spec.md`; grepped for arbitrary Tailwind values, hex
  colors, and placeholder text. Verdict: PASS, all acceptance criteria met,
  no issues found.
- **Dev-server smoke check**: `npm run dev -- --port 5183 --strictPort`
  from `frontend/` started cleanly with no errors; `GET /` returned `200`
  with the expected SPA shell; `GET /src/App.tsx` and
  `GET /src/routes/LandingPage.tsx` both resolved and transformed via Vite
  with no module errors. This confirms the app boots and serves the new
  route without a build/runtime error; it does not execute client-side JS
  (no browser tool available in this environment), so the rendered DOM/
  visual output was verified by code review rather than a live screenshot.

All acceptance criteria in this spec are met.
