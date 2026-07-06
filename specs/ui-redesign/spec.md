---
status: verified
owner: analyst
created: 2026-07-02
---

# Frontend Visual Redesign

## Problem

Every screen in the ImIn frontend looks interchangeable with any other
React starter kit. The entire app uses a flat `slate-50` background,
`blue-600` as the sole accent color dropped inline wherever needed,
`system-ui`/Inter as the only typeface, no named palette defined anywhere,
and spacing values scattered inconsistently across components. The visual
experience communicates nothing about what the app actually is — a
social, activity-finding product meant to move people toward getting out
and doing things together. There is no design system baseline: no shared
token layer, no font pairing, no type or spacing scale, no defined focus
or motion behavior. The result reads as unfinished scaffolding, not a
product users feel confident trusting or excited to use.

## Requirements

### 1. Central design token layer

A single design token layer must be established in
`frontend/src/index.css` using Tailwind v4's CSS-first theme config
(`@theme { … }`). All color, font-family, and any custom token
extensions live here. No component file may define a one-off accent
color, font-family value, or custom spacing value that is not drawn from
this layer.

### 2. Named color palette

The token layer must declare a named palette consisting of:
- One dominant/background color (replaces the current flat `slate-50`
  global background).
- One primary accent color (replaces the ad-hoc `blue-600` currently
  scattered across every interactive element).
- One secondary accent or surface color (for cards, panels, or secondary
  interactive elements).
- Semantic aliases for error, warning, and success states (currently
  hardcoded as `red-600`, `amber-50/amber-800`, `green-700` per
  component).

The palette must be energetic and specific to an activity-finding social
app. It must not be one of the following AI-default patterns: warm cream
+ high-contrast serif + terracotta, near-black + neon/acid accent,
purple gradient on white. The rationale for the palette must be stated by
the architect in their design notes.

### 3. Real font pairing

Two distinct typefaces must be applied via the Tailwind v4 CSS-first
theme:
- A display/heading face used for all page headings (`h1`, `h2`) and the
  NavBar wordmark.
- A body face used for all paragraph text, labels, inputs, and smaller
  UI elements.

`system-ui` must not be the sole or default typeface for either role.
The pairing must be loaded via a web font mechanism compatible with the
Vite build (e.g., Google Fonts import or bundled font files) and applied
through the Tailwind theme so components do not need per-element
`font-*` overrides.

### 4. One type scale

All text sizes across every screen must use Tailwind's built-in text
size tokens (`text-xs`, `text-sm`, `text-base`, `text-lg`, `text-xl`,
`text-2xl`, `text-3xl`, etc.). No arbitrary font-size values (e.g.,
`text-[15px]`) may appear in component files. The hierarchy must be
legible and intentional: heading sizes must differ meaningfully from body
sizes; body and label sizes must not be the same weight and size in the
same context.

### 5. One spacing scale

All margins, padding, and gaps across every screen must use Tailwind's
4px-based spacing tokens (`p-2`, `gap-4`, `mb-6`, etc.). No arbitrary
spacing values (e.g., `mt-[14px]`, `p-[22px]`) may appear in component
files. Spacing between logically related elements must be visibly smaller
than spacing between unrelated sections.

### 6. WCAG AA contrast

All text rendered on the redesigned screens must meet WCAG AA contrast
(minimum 4.5:1 for body text, 3:1 for large text ≥ 18px regular or
14px bold) against its background. This requirement applies to:
- Body copy and label text
- Button labels in default, hover, disabled, and focus states
- Form input text and placeholder text
- NavBar link text in default and active states
- Error, warning, and success message text
- Category badge / chip text

### 7. Visible keyboard focus states

Every interactive element — links, buttons, text inputs, textareas,
date/time inputs — must display a visible focus ring or outline that
is distinct from its default unfocused appearance. Focus styles must be
consistent across all screens (same shape and color treatment), not
browser-default only. The focus indicator must itself meet 3:1 contrast
against surrounding colors per WCAG 2.1 SC 1.4.11.

### 8. Responsive layout

All screens must render without horizontal scroll, overlapping content,
or truncated interactive elements at viewport widths of 375px, 768px,
and 1280px. The NavBar must adapt for mobile: at 375px, navigation links
must either stack, collapse behind a toggle, or be replaced with an
equivalent mobile navigation treatment that keeps all destinations
reachable.

### 9. Purposeful motion with prefers-reduced-motion support

Any CSS transitions or animations introduced by the redesign must have a
single, articulable purpose (e.g., confirming a state change, drawing
attention to a newly revealed element). Decorative hover effects or
infinite-loop animations with no informational purpose are not permitted.
All motion must be conditioned on `prefers-reduced-motion: no-preference`
so that users with the OS-level reduced-motion preference set experience
no movement.

### 10. Real ImIn copy throughout

No screen, loading state, empty state, placeholder, or error message may
use lorem ipsum, generic "Widget" or "Feature" placeholders, or content
that does not reflect the actual ImIn product. Examples of acceptable
copy:
- Group names: "Saturday Morning Run", "Hackney Bouldering Club",
  "Weekend Watercolor Painters"
- Activity names: "Evening 5K — Victoria Park", "Intro Bouldering
  Session", "Plein Air Sketch Walk"
- Empty states: "No activities scheduled yet — be the first to plan
  something.", "You have no conversations yet. Message someone from a
  group's member list to start one."
- All existing user-facing copy strings in the current components must
  be preserved or improved, never replaced with generic text.

### 11. One signature element per key screen

Each key screen listed below must have one visually distinct treatment
that makes it identifiable as that screen and could not be mistaken for
the same screen in a generic SaaS product. Supporting screens share the
system-wide design tokens but do not require their own signature element.

Key screens requiring a signature element:
- **LoginPage** — the full-page auth layout
- **HomePage** — the logged-in dashboard/landing
- **GroupsListPage** — the discovery + search page
- **GroupDetailPage** — the group detail, members, chat, and activities view
- **ActivityDetailPage** — the activity detail with location/routing section
- **NavBar** — the persistent authenticated navigation

Supporting screens (consistent design tokens, no additional requirement):
- RegisterPage, VerifyEmailPage, OAuthCallbackPage
- FriendsPage
- DirectMessagesListPage, DirectThreadPage
- ProfilePage, CreateGroupPage

The architect must name and describe the chosen signature element for
each key screen in their design notes.

### 12. No new features

This spec covers visual treatment only. No new routes, API calls, data
models, or user-facing capabilities may be added as part of this work.
Existing functionality (form submissions, error handling, chat polling,
activity creation, routing/map display) must be preserved exactly.

## Out of scope

- Backend changes of any kind
- New features or new screens beyond those listed under Requirements
- Images, illustrations, or icon sets (the MVP has none; this spec does
  not introduce them)
- Map component visual treatment (`MapView.tsx`, `RoutingControl.tsx`)
  — the tile layer styling, route line color, and map markers are a
  separate concern and must not be altered by this work
- Dark mode (not required for this pass)
- Internationalization or copy changes beyond what is needed to remove
  placeholder content

## Acceptance criteria

- [ ] `frontend/src/index.css` contains a `@theme { … }` block that
      defines at minimum: one dominant background color, one primary
      accent color, one secondary surface color, semantic error/warning/
      success color aliases, two font-family values (display and body),
      and no remaining reference to `blue-600` as a bare Tailwind
      utility used outside the theme.

- [ ] No component file under `frontend/src/` contains a bare `blue-600`
      class (the pre-redesign ad-hoc accent) — all interactive elements
      use a token from the palette defined in `index.css`.

- [ ] Two distinct typefaces (display and body) load at runtime and are
      visibly applied: `h1` and `h2` elements use the display face;
      paragraph text and form labels use the body face. Verified by
      browser DevTools computed styles on at least LoginPage and
      GroupDetailPage.

- [ ] No component file contains an arbitrary font-size value (pattern
      `text-\[.*\]`). All text sizes use Tailwind scale tokens.

- [ ] No component file contains an arbitrary spacing value (patterns
      `p-\[.*\]`, `m-\[.*\]`, `gap-\[.*\]`, `px-\[.*\]`, etc.). All
      spacing uses Tailwind scale tokens.

- [ ] A contrast check using WebAIM Contrast Checker or equivalent on
      the primary accent color against white (or whatever surface it
      appears on) returns ≥ 4.5:1. Body text on the dominant background
      returns ≥ 4.5:1. Error message text on its background returns
      ≥ 4.5:1.

- [ ] Tabbing through LoginPage in a browser reaches every interactive
      element (email input, password input, Log in button, Sign in with
      Google link, Register link) with a visible focus ring on each one
      before reaching the next.

- [ ] Tabbing through NavBar reaches every navigation link and the
      Log out button with a visible focus ring on each.

- [ ] At 375px viewport width, all screens render without a horizontal
      scrollbar and without any interactive element being clipped or
      overlapping another.

- [ ] At 375px viewport width, all NavBar destinations (Groups, Friends,
      Messages, Profile, Log out) remain reachable without horizontal
      scroll.

- [ ] With the OS reduced-motion preference enabled (macOS: System
      Settings > Accessibility > Display > Reduce Motion; or Chrome
      DevTools > Rendering > Emulate prefers-reduced-motion), no visible
      transition or animation plays on any screen.

- [ ] A grep for `lorem` (case-insensitive) across `frontend/src/`
      returns zero matches.

- [ ] The architect's design notes for this spec identify by name the
      signature element chosen for each of the six key screens (LoginPage,
      HomePage, GroupsListPage, GroupDetailPage, ActivityDetailPage,
      NavBar).

- [ ] All existing form submissions, error messages, and async state
      transitions (loading states, success confirmations, validation
      errors) that existed before the redesign still function after it.
      Verified by a manual walkthrough: register → verify email → log in
      → browse groups → open group → create activity → view activity →
      get directions.

- [ ] The chosen palette is not one of the AI-default patterns (warm
      cream + terracotta, near-black + neon, purple gradient on white).
      The architect's design notes include a one-sentence rationale for
      why the palette is right for an activity-finding social app.

## Design notes

(filled in by architect)

## Implementation notes

Implemented all 17 files in the specified order. Summary of what was built and where:

**Token layer** (`frontend/src/index.css`): Google Fonts import first (Syne 700/800 + DM Sans variable), then `@import "tailwindcss"`, then `@theme {}` block with all 12 color tokens and 2 font-family tokens. Global `body`/`h1`/`h2`/`h3` base styles. Focus ring placed inside `@layer base` (per design doc Risk 10) so component-level `focus:outline-none` can override it. `prefers-reduced-motion` block uses `!important` guards.

**MapView.tsx**: Full rewrite — exports `GroupPin` and `MapViewProps` interfaces. Returns `null` when no center can be determined (no hardcoded coordinates). Custom `L.divIcon` markers: teal `#0B6E65` for group pins, amber `#D97706` for user location. Popup includes `Link` to `/groups/${pin.id}`. No second leaflet CSS import added.

**NavBar.tsx**: Full rewrite with `useState(menuOpen)`. Two-color wordmark (`Im` text-text, `In` text-primary) using Syne via `font-display`. Mobile hamburger (SVG, no external icon library) visible below `md`, desktop links hidden below `md`. Mobile panel drops below the top bar. All interactive elements have explicit focus rings.

**LoginPage.tsx**: Full-page `bg-primary` wrapper. Wordmark + tagline above the white card. Form card uses `rounded-2xl bg-surface p-8 shadow-xl`. All inputs, buttons, and links updated to design token classes.

**RegisterPage.tsx**: Same full-page teal treatment. Confirmation card ("Check your email") also renders on the teal background in the same card treatment.

**HomePage.tsx**: Eyebrow "WELCOME BACK" label + `text-4xl font-extrabold font-display` display name. Three pill-shaped CTA buttons (one primary, two secondary/outline).

**GroupsListPage.tsx**: Added `userLocation` state. Inside `loadRecommendations`, `setUserLocation` called after `getCurrentPosition()` succeeds and before `recommendGroups`. MapView inserted between `</form>` and the search results section, gated on `!isLoadingRecommendations && !recommendationsError && recommendations?.length > 0 && userLocation`. All design tokens applied throughout. `GroupListItem` cards use `rounded-2xl border-border` with hover `border-primary/60`.

**GroupDetailPage.tsx**: Category chips moved above h1 in the non-editing header branch (signature element). Chips wrapped in `<div className="mb-3 flex flex-wrap gap-2">`. All `rounded-lg bg-white p-6 shadow` → `rounded-2xl bg-surface p-6 shadow-sm border border-border`. All primary/secondary/destructive buttons updated. Admin badge changed from `bg-blue-100 text-blue-700` → `bg-primary/10 text-primary`. Kick button uses `text-accent border-accent/30`. Empty state text updated to "No activities scheduled yet — be the first to plan something."

**ActivityDetailPage.tsx**: Group name eyebrow `<p>` added above `<h1>`. Back button changed to text-link style. RoutingControl receives `className="h-64 w-full rounded-2xl overflow-hidden"`. All cards, inputs, buttons updated.

**RoutingControl.tsx**: Polyline color changed from `#2563eb` to `#0B6E65`.

**ChatPanel.tsx**: `max-w-[80%]` → `max-w-4/5`. `text-[11px]` → `text-xs`. Own bubble `bg-primary text-on-primary`, other bubble `bg-surface text-text ring-1 ring-border`. Container height `h-72`, border `rounded-xl`.

**FriendsPage.tsx, DirectMessagesListPage.tsx, DirectThreadPage.tsx, ProfilePage.tsx, CreateGroupPage.tsx**: Global substitution patterns applied. Category toggle chips in ProfilePage and CreateGroupPage use the selected (`bg-primary text-on-primary`) / unselected (`border border-border bg-background text-text-muted hover:bg-primary/10 hover:text-primary`) pattern.

**VerifyEmailPage.tsx, OAuthCallbackPage.tsx**: Global substitutions applied (bg-background, bg-surface, text-text, text-error, text-primary, font-body, rounded-2xl cards, rounded-full buttons).

**Deviations from brief**: None. All arbitrary values removed (confirmed by grep returning zero matches). All bare `blue-600` classes removed (confirmed by grep). TypeScript compiles with no errors (`tsc --noEmit` clean).

## Verification

(filled in by tester)
