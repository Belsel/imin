---
status: in-progress
specs:
  - specs/ui-redesign/spec.md
  - specs/group-map-discovery/spec.md
created: 2026-07-02
---

# Technical Design: UI Redesign + Group Map Discovery

These two specs share a single token layer: the map card on GroupsListPage
must consume the same design-system tokens being defined by the visual
redesign. Both are designed here together. The map work adds one new
component contract (MapViewProps) and a small state addition to
GroupsListPage. Everything else is visual treatment applied to existing
logic.

---

## 1. Visual Identity Decision Log

The frontend-aesthetics skill requires a two-pass design process: draft a
plan, self-critique it against generic AI defaults, then fix anything that
would look right at home in a starter kit. Each decision below follows that
process explicitly.

---

### 1.1 Palette

**What this screen is for, who uses it, what its single job is.**
Every screen in this app serves someone trying to find or host a local
meetup — running group, climbing session, watercolor walk. They're likely
on mobile, outdoors or commuting. The palette must read as alive and
participatory the instant the screen loads, not as a productivity tool.

**Draft.**
Energetic outdoor apps often reach for orange or lime green. Neither
is wrong but both are clichés. Something more specific to what ImIn
actually is: a place where strangers become regulars together. The color
of a well-lit community sports facility — pool tiles, court line tape,
open-air track surfaces — is a deep, confident teal. Not neon, not
turquoise, not healthcare-blue: the specific shade of public leisure
infrastructure you trust will be there every Saturday morning. Pair it
with a warm amber as a counterpoint: the late-afternoon light of the
activity you're trying to get to.

Core tokens (4 dominant palette entries + 2 supporting):

| Token | Hex | Role |
|---|---|---|
| `--color-background` | `#F0FDFA` | Page-level background (Tailwind teal-50 exactly); a barely-perceptible cyan tint that reads as "alive" vs. the current dead slate-50 |
| `--color-surface` | `#FFFFFF` | Card, panel, and form container fill |
| `--color-primary` | `#0B6E65` | All primary interactive elements: buttons, active links, focus rings |
| `--color-on-primary` | `#FFFFFF` | Text/icons rendered on top of a primary-colored surface |
| `--color-accent` | `#D97706` | Amber-600; decorative large-text highlights only (see WCAG note below) |
| `--color-text` | `#0F172A` | Body copy and headings on the background or surface |

**Self-critique.** Is teal+amber what any AI prompt would produce for
"make it outdoorsy"? No — the usual defaults are green+brown (hiking),
orange+black (Strava-clone), or bright lime (running/fitness). Teal has no
clichéd sport association: it's the color of community, of public pools,
of painted lane markings on shared courts. The barely-tinted background
(#F0FDFA) is genuinely unusual — almost no app uses a cyan-tinted canvas —
which makes white cards read as clean and deliberate rather than default.
The amber accent is the only warm element in an otherwise cool palette,
which makes it pop without shouting. This palette could only be ImIn's.
**Keep it.**

**Extended tokens** (semantic aliases and supporting values added alongside
the 6 core):

```
--color-text-muted:    #475569   /* slate-600; secondary/muted text */
--color-border:        #CBD5E1   /* slate-300; dividers, input borders */
--color-error:         #DC2626   /* red-600 */
--color-warning:       #FFFBEB   /* amber-50; warning banner background */
--color-warning-text:  #92400E   /* amber-800; warning banner text */
--color-success:       #15803D   /* green-700; success confirmation text */
```

**WCAG AA contrast verification (all must pass before implementation ships).**

| Pair | Approximate ratio | Pass? |
|---|---|---|
| `--color-text` (#0F172A) on `--color-background` (#F0FDFA) | ~15.8:1 | AA body text ✓ |
| `--color-text` (#0F172A) on `--color-surface` (#FFFFFF) | ~19.1:1 | AA body text ✓ |
| `--color-text-muted` (#475569) on `--color-surface` (#FFFFFF) | ~7.7:1 | AA body text ✓ |
| `--color-text-muted` (#475569) on `--color-background` (#F0FDFA) | ~6.5:1 | AA body text ✓ |
| `--color-on-primary` (#FFFFFF) on `--color-primary` (#0B6E65) | ~6.1:1 | AA body text ✓ |
| `--color-primary` (#0B6E65) on `--color-surface` (#FFFFFF) | ~6.1:1 | AA body text ✓ (text links) |
| `--color-error` (#DC2626) on `--color-surface` (#FFFFFF) | ~4.8:1 | AA body text ✓ |
| `--color-success` (#15803D) on `--color-surface` (#FFFFFF) | ~5.0:1 | AA body text ✓ |
| `--color-warning-text` (#92400E) on `--color-warning` (#FFFBEB) | ~6.8:1 | AA body text ✓ |
| `--color-accent` (#D97706) on `--color-surface` (#FFFFFF) | ~3.2:1 | FAILS 4.5:1 — large text only |

**Critical WCAG constraint on `--color-accent`:** `#D97706` achieves only
~3.2:1 against white, which passes the 3:1 threshold for large text
(18px+ regular weight, or 14px+ bold) but fails 4.5:1 for normal body text
and labels. The accent color must NEVER be used as text on a light surface
at normal sizes. Permitted uses: the NavBar wordmark "In" at `text-xl
font-extrabold` (20px bold qualifies as large text; 3.2:1 >= 3:1 ✓);
decorative focus-ring–adjacent highlight; background tint for hover states
where no text sits directly on the amber fill.

---

### 1.2 Typography

**Draft.**
The current app uses only `system-ui`. Two faces are required. For the
display role (headings and the NavBar wordmark): **Syne** — a geometric
sans with subtly unusual letterform construction (the G, Q, and R
have just enough eccentricity to read as deliberate without being
difficult). Syne is used by independent studios and creative agencies,
never by generic SaaS dashboards. For the body role: **DM Sans** — a
humanist geometric with excellent legibility at 14px, purpose-built for
screen UI. DM Sans has a variable font axis (`opsz`, `wght`) on Google
Fonts, which keeps the weight range smooth at every label size.

**Self-critique.** "Bold geometric display + clean humanist body" is a
competent pairing but is it specific to ImIn? The reason it fits: Syne's
slightly irregular stroke construction communicates the handmade energy
of community events (a running club poster, a bouldering gym chalk
notice board), while DM Sans's functional clarity keeps the bulk of the
interface — group descriptions, chat messages, form labels — effortless
to read. The pairing says "designed by someone who cares" without
performing effort for its own sake. **Keep it.**

**Google Fonts import URL (single combined request):**
```
https://fonts.googleapis.com/css2?family=Syne:wght@700;800&family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,500;0,9..40,600&display=swap
```

**Tailwind v4 theme tokens:**
```css
--font-display: 'Syne', sans-serif;
--font-body:    'DM Sans', sans-serif;
```

These create `font-display` and `font-body` Tailwind utility classes
that components apply directly. No per-element inline `font-family`
declarations anywhere in component files.

**Type scale (Tailwind tokens only — no arbitrary values):**

| Role | Tailwind classes | Usage |
|---|---|---|
| Page h1 | `text-3xl font-bold font-display` | One per page; GroupsListPage "Groups", LoginPage brand logotype above form |
| Section h2 | `text-2xl font-bold font-display` | Card/section headings: "Members", "Group chat", "Activities" |
| Sub-heading h3 | `text-xl font-semibold font-display` | In-card secondary headings |
| Body | `text-base font-body` | Paragraphs, descriptions, chat messages |
| Label | `text-sm font-body font-medium` | Form labels, member names in lists |
| Caption / meta | `text-xs font-body` | Timestamps, distances, "3 members", admin badges |

Existing code that uses `text-xl font-semibold` for page h1s must be
upgraded to `text-3xl font-bold font-display`. Existing `text-lg font-semibold`
section headings become `text-2xl font-bold font-display`. The scale step
is intentional: current h1/h2 sizes are too close together.

---

### 1.3 Signature elements

Each key screen has exactly one deliberate visual treatment that could not
be mistaken for a generic SaaS screen. Everything around the signature is
quiet and disciplined.

**LoginPage** — Full-page primary background with floating form card.

The entire viewport fills with `bg-primary` (#0B6E65). Centered in the
vertical middle: the ImIn logotype at `text-5xl font-extrabold font-display
text-on-primary tracking-tight`, then a one-line brand manifesto — "Find
your people. Get ImIn." — in `text-sm text-on-primary/70 tracking-wide
uppercase`, then the white form card (`bg-surface rounded-2xl p-8
shadow-xl max-w-sm w-full`). The form itself is unmodified in structure;
only the backdrop changes. No other screen in the app is teal-background,
so LoginPage is immediately distinct. The contrast from primary to surface
inside the card is sharp; the wordmark visible above the card confirms brand
before credentials are entered. RegisterPage receives the same full-page
treatment for visual consistency (RegisterPage is a supporting screen, not a
key screen, but consistency here is correct).

**HomePage** — Eyebrow-labeled welcome headline in the display font.

Immediately below the NavBar, centered in the viewport: a label line in
`text-xs font-semibold font-body tracking-[0.2em] uppercase text-primary
mb-2` reading "WELCOME BACK", then the user's display name at `text-4xl
font-extrabold font-display text-text`, then the three action CTAs
(`Browse groups`, `Friends & blocks`, `Profile`) as full pill-shaped primary
buttons rather than the current tiny inline links. The eyebrow + massive
first-name treatment makes returning to the app feel like an event, not a
loading state. No other screen uses the name at this scale.

**GroupsListPage** — Interactive map rendered above the recommendations list.

The map is the signature element. It is the only page in the app where a
discovery map appears above a browseable list. When geolocation succeeds,
the map card (h-56 / 224px, full container width, rounded-2xl) sits between
the search bar and the "Recommended for you" section, showing the user's
position and all recommended group pins simultaneously. The group cards in
the list below gain a left-accent border (`border-l-4 border-l-primary/40
hover:border-l-primary`) on hover, creating a visual echo of the map's pin
markers below. No other screen has this map-above-list composition.

**GroupDetailPage** — Category-chip eyebrow above the group name in the detail header card.

The group's categories are rendered in a horizontal wrap at the TOP of the
group header card, BEFORE the group name — inverting the usual "name first,
tags below" hierarchy. These chips use `bg-primary/10 text-primary text-xs
font-medium rounded-full px-3 py-1`. Below them, the group name renders in
`text-3xl font-bold font-display text-text`. Seeing the categories first
establishes what kind of group this is before the name is read, which is
the correct information priority for a discovery flow.

**ActivityDetailPage** — Activity-type eyebrow label above the activity name.

Immediately above the `h1` (the activity name), a single line reads the
parent group's name in `text-xs font-semibold font-body tracking-[0.2em]
uppercase text-primary mb-1`. This replaces the current "← Back to {name}"
button as the context-setting mechanism and anchors the activity within
its group at the typographic level rather than only in the navigation. The
back button is retained for navigation but the eyebrow carries the context.
The activity name then appears in `text-3xl font-bold font-display text-text`.

**NavBar** — Two-color wordmark using the brand name's inherent structure.

The "ImIn" wordmark renders as two typographic fragments: `<span
class="text-text">Im</span><span class="text-primary">In</span>` at
`text-xl font-extrabold font-display tracking-tight`. The split at the
brand name's natural word boundary — "Im" (personal pronoun, first person)
and "In" (belonging) — reinforces the app's identity in the logo itself.
The NavBar background becomes `bg-surface border-b-2 border-primary` —
a thicker bottom border in the primary color that grounds every page's
chrome in the brand without being visually heavy.

---

## 2. Component-Level Class Changes

### Global substitution patterns

These patterns apply across all component files. The implementer applies
them mechanically after the global patterns are established in index.css.

| Remove | Replace with | Notes |
|---|---|---|
| `bg-slate-50` | `bg-background` | Page container backgrounds |
| `bg-white` | `bg-surface` | All card and panel backgrounds |
| `bg-blue-600` | `bg-primary` | All primary buttons and filled accent elements |
| `hover:bg-blue-700` | `hover:bg-primary/90` | Primary button hover |
| `text-blue-600` | `text-primary` | All text links |
| `rounded-lg bg-white p-6 shadow` | `rounded-2xl bg-surface p-6 shadow-sm border border-border` | All content cards |
| `rounded border border-slate-300 px-3 py-2 text-slate-900` | `rounded-lg border border-border bg-surface px-3 py-2 text-text font-body focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20` | All form inputs and textareas |
| `rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50` | `rounded-full bg-primary px-4 py-2 font-medium text-on-primary transition-colors motion-safe:hover:bg-primary/90 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50` | Primary button |
| `rounded border border-slate-300 px-4 py-2 font-medium text-slate-700 hover:bg-slate-50` | `rounded-full border border-border px-4 py-2 font-medium text-text-muted transition-colors motion-safe:hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2` | Secondary/outline button |
| `rounded border border-red-300 px-4 py-2 font-medium text-red-700 hover:bg-red-50` | `rounded-full border border-error/30 px-4 py-2 font-medium text-error transition-colors motion-safe:hover:bg-error/5 focus:outline-none focus:ring-2 focus:ring-error focus:ring-offset-2` | Destructive button |
| `rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-700` | `rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary` | Read-only category chips (GroupsListPage, GroupDetailPage list items) |
| `rounded-full px-3 py-1 text-sm font-medium` selected chip | `rounded-full bg-primary px-3 py-1 text-sm font-medium text-on-primary` | Selected toggle chip (ProfilePage, CreateGroupPage) |
| `rounded-full px-3 py-1 text-sm font-medium` unselected chip | `rounded-full border border-border bg-background px-3 py-1 text-sm font-medium text-text-muted motion-safe:hover:bg-primary/10 motion-safe:hover:text-primary` | Unselected toggle chip |
| `text-slate-900` | `text-text` | All primary text |
| `text-slate-700`, `text-slate-600`, `text-slate-500` | `text-text-muted` | All secondary/muted text |
| `text-red-600`, `text-red-700` | `text-error` | Error messages |
| `bg-red-50 px-3 py-2 text-sm text-red-700` | `bg-error/8 px-3 py-2 text-sm text-error rounded-lg` | Error banners |
| `bg-amber-50 px-3 py-2 text-sm text-amber-800` | `bg-warning px-3 py-2 text-sm text-warning-text rounded-lg` | Warning banners |
| `text-green-700` | `text-success` | Success confirmation text |
| `border-slate-200`, `border-slate-300` | `border-border` | All borders |
| `h2 className ... text-lg font-semibold text-slate-900` | `text-2xl font-bold font-display text-text` | Section headings |
| `h1 className ... text-2xl font-semibold text-slate-900` | `text-3xl font-bold font-display text-text` | Page headings |
| `text-[11px]` (ChatPanel.tsx line 76) | `text-xs` | Fix arbitrary value |
| `max-w-[80%]` (ChatPanel.tsx line 65) | `max-w-4/5` | Fix arbitrary value (Tailwind v4 supports fraction-based max-w) |

### File-by-file changes

**`frontend/src/index.css`** — See Section 5 for the complete final file
content. This is the only new content; no existing tokens are removed from
Tailwind's built-in scale.

**`frontend/src/components/NavBar.tsx`**

The NavBar requires structural changes for both the brand treatment and
mobile responsiveness. The implementer must:

1. Add `useState` for `menuOpen: boolean`.
2. At viewport < `md` (below 768px), replace the horizontal link row with a
   hamburger toggle button (three-line icon in SVG, no external icon
   library). When `menuOpen` is true, render the links as a vertical stack
   in a full-width dropdown panel below the top bar. The hamburger button
   receives a visible focus ring (`focus:outline-none focus:ring-2
   focus:ring-primary focus:ring-offset-2`).
3. The outer `<nav>` becomes:
   `flex flex-col border-b-2 border-primary bg-surface`
4. The top row: `flex items-center justify-between px-6 py-3`
5. The wordmark Link to="/": `text-xl font-extrabold font-display
   tracking-tight` wrapping two `<span>` elements: the first `text-text`
   ("Im"), the second `text-primary` ("In").
6. Desktop link row (`hidden md:flex items-center gap-6`): each Link
   becomes `text-sm font-medium font-body text-text-muted
   transition-colors motion-safe:hover:text-primary
   focus:outline-none focus:ring-2 focus:ring-primary focus:rounded`.
7. Mobile menu panel (rendered below the top row when `menuOpen`, hidden
   on md+): `md:hidden border-t border-border bg-surface px-6 py-4 flex
   flex-col gap-3`. Each link: `text-sm font-medium font-body text-text-muted
   motion-safe:hover:text-primary py-1`.
8. Log out button becomes the secondary button style:
   `rounded-full border border-border px-3 py-1.5 text-sm font-medium
   text-text-muted transition-colors motion-safe:hover:bg-background
   focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2`.

**`frontend/src/routes/LoginPage.tsx`**

Full structure change for the signature element:

- Outer wrapper: `flex min-h-screen flex-col items-center justify-center
  gap-6 bg-primary p-6`
- Add a new element above the form: a `<div>` with `text-center` containing
  the `<h1>` at `text-5xl font-extrabold font-display text-on-primary
  tracking-tight` ("ImIn") and a `<p>` at `text-sm text-on-primary/70
  tracking-wide uppercase mt-2` ("Find your people. Get ImIn.")
- The `<form>` card: `w-full max-w-sm rounded-2xl bg-surface p-8 shadow-xl`
- The h1 inside the form ("Log in") changes to `text-2xl font-bold
  font-display text-text mb-6`
- All inputs: apply global input pattern
- Primary submit button: apply global primary button pattern with `w-full`
- Google sign-in link: apply secondary/outline button pattern with `w-full
  text-center mt-3 block`
- Error state `<p>`: apply `text-sm text-error`
- Unverified banner: apply `bg-warning px-3 py-2 text-sm text-warning-text
  rounded-lg mb-4`
- Register link: `text-primary hover:underline`

**`frontend/src/routes/RegisterPage.tsx`**

Same outer structure as LoginPage (full-page primary background, wordmark
above the form card). The form card h1 changes to "Create an account" in
`text-2xl font-bold font-display text-text mb-6`. All inputs and buttons
follow global patterns. Link to `/login` becomes `text-primary
hover:underline`. Post-registration confirmation card ("Check your email")
also appears on the teal background, same card treatment.

**`frontend/src/routes/HomePage.tsx`**

- Outer: `min-h-screen bg-background` (no longer bg-slate-50)
- Content area: `flex flex-col items-center justify-center gap-6 p-6 mt-16`
- Replace `<h1 className="text-2xl font-semibold text-slate-900">Welcome,
  {user?.displayName}</h1>` with a two-element structure:
  - `<p className="text-xs font-semibold font-body tracking-[0.2em] uppercase
    text-primary">WELCOME BACK</p>`
  - `<h1 className="text-4xl font-extrabold font-display text-text">
    {user?.displayName}</h1>`
- Email line: `text-sm text-text-muted font-body` (reduce prominence)
- Button group: `flex flex-wrap gap-3 justify-center`
- "Browse groups" becomes the primary button style
- "Friends & blocks" and "Profile" become secondary/outline button style

**`frontend/src/routes/GroupsListPage.tsx`**

In addition to all global class replacements:

1. Add state: `const [userLocation, setUserLocation] = useState<{ lat:
   number; lng: number } | null>(null)`
2. Inside `loadRecommendations`, after `const position =
   await getCurrentPosition()` succeeds, add:
   `setUserLocation({ lat: position.latitude, lng: position.longitude })`
3. Import `MapView` and `Link` (Link is already imported).
4. Between `</form>` and the search results `<section>`, insert the map:
   ```tsx
   {!isLoadingRecommendations &&
    !recommendationsError &&
    recommendations &&
    recommendations.length > 0 &&
    userLocation && (
     <div className="mb-6">
       <MapView
         groupPins={recommendations.map((g) => ({
           id: g.id,
           name: g.name,
           lat: g.latitude,   // implementer: verify exact field name in GroupRecommendationResponse
           lng: g.longitude,  // implementer: verify exact field name in GroupRecommendationResponse
         }))}
         userLocation={userLocation}
       />
     </div>
   )}
   ```
5. GroupListItem card: `block rounded-2xl border border-border bg-surface
   p-4 shadow-sm transition-colors motion-safe:hover:border-primary/60
   motion-safe:hover:shadow border-l-4 border-l-primary/0
   motion-safe:hover:border-l-primary` — the left-border accent on hover.
   (Note: the two `border-l-*` classes create the hover left-accent effect;
   on default state the left border is transparent / color-zero.)
6. Page heading: `text-3xl font-bold font-display text-text`
7. "Create a group" button: primary button style
8. Search input: global input pattern
9. "Search" button: primary button style (distinct from outline to signal it
   is the primary action on this form)
10. Category chips in GroupListItem: `rounded-full bg-primary/10 px-3 py-1
    text-xs font-medium text-primary`
11. Warning banner (recommendation error): `bg-warning px-3 py-2 text-sm
    text-warning-text rounded-lg`

**`frontend/src/routes/GroupDetailPage.tsx`**

Pattern-level description (the file is large; the implementer applies the
global substitution table):

- All `rounded-lg bg-white p-6 shadow` cards → `rounded-2xl bg-surface p-6
  shadow-sm border border-border`
- All `bg-blue-600` primary buttons → global primary button pattern
- All `rounded border border-slate-300 px-3 py-1.5 text-sm font-medium
  text-slate-700 hover:bg-slate-50` secondary buttons → secondary button
  pattern (sized at `px-3 py-1.5 text-sm`)
- Destructive buttons → destructive button pattern
- All form inputs/textareas → global input pattern
- All section `h2` → `text-2xl font-bold font-display text-text mb-3`
- The group header card: signature element applies here:
  - Move the categories `.map()` chip row to render ABOVE the h1, with the
    chip row wrapped in `<div className="mb-3 flex flex-wrap gap-2">` and
    chips at `rounded-full bg-primary/10 px-3 py-1 text-xs font-medium
    text-primary`
  - `h1` becomes `text-3xl font-bold font-display text-text`
- Admin badge (`bg-blue-100 text-blue-700`) → `bg-primary/10 text-primary`
- Activity list items: `block rounded-xl border border-border px-4 py-3
  transition-colors motion-safe:hover:border-primary/60`
- `bg-slate-50 p-4` create-activity form inner container: `bg-background
  rounded-xl border border-border p-4`

**`frontend/src/routes/ActivityDetailPage.tsx`**

Pattern-level description:

- All cards → global card pattern
- All buttons → global button patterns by role
- All inputs → global input pattern
- `h1` for activity name → preceded by the signature eyebrow element:
  `<p className="text-xs font-semibold font-body tracking-[0.2em] uppercase
  text-primary mb-1">{group.name}</p>` immediately above the `<h1
  className="text-3xl font-bold font-display text-text">`
- The "← Back to {group.name}" button: `text-sm font-medium font-body
  text-primary motion-safe:hover:underline mb-4` (styled as a text link
  rather than a bordered button)
- The turn-by-turn steps `<ol>`: each `<li>` → `rounded-xl border
  border-border px-3 py-2`
- `RoutingControl` component class: the className prop passed to it
  should become `h-64 w-full rounded-2xl overflow-hidden` (replacing the
  current default `h-96 w-full rounded-lg shadow`)

**`frontend/src/components/ChatPanel.tsx`**

- Chat messages container: `mb-3 flex h-72 flex-col gap-2 overflow-y-auto
  rounded-xl border border-border bg-background p-3` (reduces to h-72 from
  h-80 for a slightly less dominant panel; bg-background instead of
  bg-slate-50)
- Own message bubble: `max-w-4/5 rounded-2xl px-3 py-2 text-sm bg-primary
  text-on-primary`
- Other message bubble: `max-w-4/5 rounded-2xl px-3 py-2 text-sm bg-surface
  text-text shadow-sm ring-1 ring-border`
- Timestamp: `mt-0.5 text-xs text-text-muted` (replace `text-[11px]` →
  `text-xs` to remove the arbitrary value)
- Message input: global input pattern with `flex-1`
- Send button: primary button style at `px-4 py-2`

**`frontend/src/components/MapView.tsx`**

Full rewrite of props interface and rendering. See Section 3 for the
complete specification.

**`frontend/src/routes/FriendsPage.tsx`**,
**`frontend/src/routes/DirectMessagesListPage.tsx`**,
**`frontend/src/routes/DirectThreadPage.tsx`**,
**`frontend/src/routes/ProfilePage.tsx`**,
**`frontend/src/routes/CreateGroupPage.tsx`**

These supporting screens apply the global substitution table only. No
signature element is required. Category toggle chips in ProfilePage and
CreateGroupPage use the selected/unselected chip pattern from the global
table. The DirectMessagesListPage thread rows: `flex items-center
justify-between rounded-xl border border-border px-3 py-3
transition-colors motion-safe:hover:bg-background`.

---

## 3. MapView Component Redesign

### TypeScript interface

```ts
interface GroupPin {
  id: number
  name: string
  lat: number
  lng: number
}

interface MapViewProps {
  groupPins: GroupPin[]         // recommended groups to show as markers
  userLocation?: { lat: number; lng: number }  // optional "you are here" pin
  centerLat?: number            // overrides map center latitude
  centerLng?: number            // overrides map center longitude
}
```

The `centerLat`/`centerLng` props are included for forward-compatibility but
GroupsListPage will not pass them — the map always centers on `userLocation`
in this use case.

### Render condition

```ts
const center: [number, number] | null =
  centerLat !== undefined && centerLng !== undefined
    ? [centerLat, centerLng]
    : userLocation
      ? [userLocation.lat, userLocation.lng]
      : null

if (!center) return null
```

When `center` is null (no `userLocation`, no explicit center), the
component returns null — no map is rendered, no error is thrown.

### How GroupsListPage passes data

GroupsListPage already fetches both the user's coordinates (from
`getCurrentPosition()`) and the recommendations list (from `recommendGroups`)
in its single `loadRecommendations` effect. The only addition is storing
the position coordinates in a `userLocation` state variable after geolocation
succeeds, then passing them (and the mapped `groupPins`) to MapView. No
second API call is made. The map and the recommendations list are both
driven by the same `recommendations` state.

Implementer note: the exact field names on `GroupRecommendationResponse` for
latitude and longitude must be verified from `frontend/src/lib/apiClient.ts`
before writing the `.map()` call. The group-map-discovery spec asserts
"recommendGroups already returns latitude/longitude per group" — the
Java-side convention is `latitude`/`longitude`. If the TypeScript type uses
those names, the mapping call is:
```ts
groupPins={recommendations.map((g) => ({
  id: g.id,
  name: g.name,
  lat: g.latitude,
  lng: g.longitude,
}))}
```

### Marker styling

Standard Leaflet `Marker` uses a PNG icon that is broken in bundled Vite
builds (the existing `frontend/src/lib/leafletIconFix.ts` already patches
the default icon path — do not remove that import). Custom colored markers
require `L.divIcon` with inline HTML:

**Group pin marker** (teal, matches primary):
```ts
import L from 'leaflet'

const groupIcon = L.divIcon({
  className: '',
  html: '<div style="width:14px;height:14px;border-radius:50%;background:#0B6E65;border:2px solid white;box-shadow:0 1px 4px rgba(0,0,0,0.35)"></div>',
  iconSize: [14, 14],
  iconAnchor: [7, 7],
  popupAnchor: [0, -10],
})
```

**User location marker** (amber, visually distinct, slightly larger):
```ts
const userIcon = L.divIcon({
  className: '',
  html: '<div style="width:18px;height:18px;border-radius:50%;background:#D97706;border:3px solid white;box-shadow:0 1px 6px rgba(0,0,0,0.4)"></div>',
  iconSize: [18, 18],
  iconAnchor: [9, 9],
  popupAnchor: [0, -12],
})
```

The hex values for inline styles are hard-coded to the design-system hex
values (`#0B6E65`, `#D97706`) because Tailwind utility classes inside a
`divIcon` HTML string are not processed by the Tailwind JIT (the string is
injected into the DOM outside React's rendering scope). This is the correct
and only viable approach for custom Leaflet icon colors.

### Popup content

```tsx
<Popup>
  <p className="font-medium text-sm">{pin.name}</p>
  <Link
    to={`/groups/${pin.id}`}
    className="text-xs text-primary hover:underline"
  >
    View group →
  </Link>
</Popup>
```

`Link` from react-router works inside a react-leaflet `<Popup>` because
react-leaflet renders popup content inside the React tree (via React portals
that remain within the tree). The router context is available. If testing
reveals routing issues with the popup Link, fall back to `<a
href={/groups/${pin.id}}>`.

### Map container classes

```tsx
<MapContainer
  center={center}
  zoom={13}
  scrollWheelZoom={false}
  className="h-56 w-full rounded-2xl"
  style={{ overflow: 'hidden' }}
>
```

`h-56` is `14rem` / `224px` — sufficient spatial context on the discovery
page without dominating the viewport. `w-full` makes it fill its container
at all breakpoints. `rounded-2xl` matches the card system. The inline
`overflow: 'hidden'` is required because `MapContainer` generates a div
that Leaflet controls directly; Tailwind's `overflow-hidden` class may not
apply reliably to the Leaflet-managed container, but an inline style will.

The wrapper div in GroupsListPage:
```tsx
<div className="mb-6">
  <MapView groupPins={...} userLocation={userLocation} />
</div>
```

### Position in GroupsListPage

The MapView renders between `</form>` (the search bar) and the search
results `<section>` if search has been performed, or between `</form>` and
the "Recommended for you" `<section>` if no search has been run. It must
not appear above the search form (the search bar is the primary action on
this page; the map is context for the recommendations below it).

Render condition: `!isLoadingRecommendations && !recommendationsError &&
recommendations && recommendations.length > 0 && userLocation !== null`.

---

## 4. Leaflet CSS

`leaflet/dist/leaflet.css` is already imported at line 4 of
`frontend/src/main.tsx`:

```ts
import 'leaflet/dist/leaflet.css'
```

No action required. The implementer must not add a second import.

---

## 5. index.css Final Shape

```css
@import url('https://fonts.googleapis.com/css2?family=Syne:wght@700;800&family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,500;0,9..40,600&display=swap');
@import "tailwindcss";

@theme {
  /* Core palette — 6 properties */
  --color-background:    #F0FDFA;
  --color-surface:       #FFFFFF;
  --color-primary:       #0B6E65;
  --color-on-primary:    #FFFFFF;
  --color-accent:        #D97706;
  --color-text:          #0F172A;

  /* Extended colour tokens */
  --color-text-muted:    #475569;
  --color-border:        #CBD5E1;

  /* Semantic aliases */
  --color-error:         #DC2626;
  --color-warning:       #FFFBEB;
  --color-warning-text:  #92400E;
  --color-success:       #15803D;

  /* Font families */
  --font-display: 'Syne', sans-serif;
  --font-body:    'DM Sans', sans-serif;
}

/* Global base styles */
body {
  font-family: var(--font-body);
  color: var(--color-text);
  background-color: var(--color-background);
}

h1,
h2,
h3 {
  font-family: var(--font-display);
}

/* Consistent focus ring across all interactive elements */
*:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
  border-radius: 4px;
}

/* Prefers-reduced-motion: disable all transitions and animations */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
  }
}
```

Notes on this file:

- The Google Fonts `@import url(...)` must be the first line in the file.
  CSS requires all `@import` statements to precede any other rules; placing
  the Tailwind import first would make the Google Fonts import invalid in
  some browsers.
- All `--color-*` variables defined inside `@theme {}` become Tailwind
  utility classes automatically in v4: `bg-primary`, `text-primary`,
  `border-border`, `bg-on-primary`, `text-warning-text`, etc.
- All `--font-*` variables become Tailwind font-family utilities: `font-display`,
  `font-body`.
- The `*:focus-visible` block provides the consistent system-wide focus ring
  required by the spec. This replaces browser-default outlines. The
  `border-radius: 4px` prevents the ring from being a harsh sharp rectangle
  on pill-shaped buttons.
- The `prefers-reduced-motion` block uses `!important` to guarantee that
  Tailwind utility classes with `transition-*` or `animate-*` are suppressed.
  The `motion-safe:` Tailwind variant on individual component classes is a
  second line of defense.

---

## 6. Migration Risk Flags

**Risk 1 — Tailwind v4 `@theme` vs `theme()` function.**
In Tailwind v4, the `theme()` CSS function is available but resolves
against the theme's generated CSS custom properties, not the old
`tailwind.config.js` key paths. In practice: do not write `theme('colors.primary')`
in component styles — it will not resolve because the key path for custom
tokens differs. Components must use only Tailwind utility class names
(`bg-primary`, `text-primary`) or direct `var(--color-primary)` in the
rare case of a style attribute. The `theme()` function is not needed at all
in this redesign.

**Risk 2 — `@theme` must be at the top level.**
Nesting `@theme {}` inside any other rule (`@media`, `@layer`, a selector)
is invalid in Tailwind v4 and will silently produce no utilities. The
`@theme {}` block must appear at the root of `index.css`, outside any
block. The file structure in Section 5 is correct.

**Risk 3 — Opacity modifier syntax on custom tokens.**
In Tailwind v4, `bg-primary/90` (90% opacity) works because Tailwind v4
generates utilities that accept the slash-opacity modifier for `@theme`
color tokens. However, the underlying CSS uses `color-mix()` rather than
the old RGB-channel approach, which requires a browser that supports
`color-mix()` in CSS (all evergreen browsers do). This is not a risk for
the target audience but should be noted.

**Risk 4 — Leaflet z-index conflicts with the NavBar.**
Leaflet sets internal z-index values: tile pane at 200, marker pane at
400, popup pane at 700. The current NavBar is `position: static` with no
z-index, so there is no conflict. **If the NavBar is ever made `sticky
top-0`** (not part of this spec but a likely future change), it must be
given `z-50` (z-index: 50) to sit below Leaflet's popup layer (700) but
above the tile layer (200). A sticky NavBar without an explicit z-index
would disappear behind open Leaflet popups. Flag this for the implementer
in case they add `sticky` while implementing the NavBar restructure for
mobile.

**Risk 5 — Leaflet CSS already imported; do not double-import.**
`main.tsx` already contains `import 'leaflet/dist/leaflet.css'` at line 4.
Adding a second import (e.g., inside MapView.tsx or index.css) will produce
duplicated CSS rules, which is harmless but wasteful. Do not add another
import.

**Risk 6 — react-leaflet `MapContainer` center/zoom are initial-only props.**
After a `MapContainer` mounts, changing the `center` or `zoom` props has no
effect — react-leaflet treats them as initial values only. For the
GroupsListPage use case this is safe: the map only renders after
`userLocation` is set (the render condition gates on it), so the initial
center will always be the user's real coordinates. If the map were rendered
before geolocation completes and then the center changed, a `key` prop would
be needed to force remount. The current render condition prevents this.

**Risk 7 — `link` inside Leaflet `Popup` and router context.**
react-leaflet renders `Popup` children inside a React portal that remains
within the React component tree, so the Router context from `BrowserRouter`
in `main.tsx` is available. Using `<Link to={...}>` inside a Popup is
expected to work. If a testing environment mounts MapView without a
`BrowserRouter` wrapper, the Link will throw. Test MapView inside a
`MemoryRouter` wrapper.

**Risk 8 — DM Sans variable font axis specification.**
The Google Fonts URL specifies the optical-size axis (`opsz`) alongside
`wght`. If the `opsz` axis is omitted from the URL, the browser falls back
to a static-weight version of DM Sans that may not render the 400/500/600
weight range correctly. The URL in Section 5 includes both axes explicitly.
Do not simplify the URL.

**Risk 9 — `max-w-[80%]` in ChatPanel.tsx.**
This arbitrary value (ChatPanel.tsx line 65) violates the spec's acceptance
criterion. In Tailwind v4, the fraction-based max-width `max-w-4/5` (which
computes to 80%) is a valid token. The implementer must replace `max-w-[80%]`
with `max-w-4/5`. If Tailwind v4's version in use does not support `max-w-4/5`,
the fallback is `max-w-xs` (20rem) which constrains message bubbles to a
fixed maximum rather than a percentage — acceptable for chat UI.

**Risk 10 — CSS specificity of `*:focus-visible` vs component-level focus classes.**
The global `*:focus-visible` rule in index.css uses the universal selector
with a pseudo-class (specificity: 0,1,0). Any component-level class like
`focus:outline-none` on a button (specificity: 0,1,0 when applied via
Tailwind's generated class) may tie or lose to the global rule depending on
declaration order. Tailwind v4 generates utility classes inside `@layer
utilities`, which has lower priority than author styles outside a `@layer`.
This means the global `*:focus-visible` in index.css (outside `@layer`) will
win over `focus:outline-none` in component classes. **Resolution:** move the
focus ring global styles inside `@layer base` in index.css:
```css
@layer base {
  *:focus-visible {
    outline: 2px solid var(--color-primary);
    outline-offset: 2px;
    border-radius: 4px;
  }
}
```
With this, `focus:outline-none` on specific components (e.g., to suppress the
ring on a button that has its own `focus:ring-2 focus:ring-primary`) can
correctly override the base rule, because utility classes (`@layer utilities`)
have higher specificity than `@layer base`. The index.css in Section 5 shows
the `*:focus-visible` block outside a `@layer` for clarity, but the implementer
should wrap it in `@layer base` to avoid this specificity trap.
