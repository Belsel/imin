---
name: frontend-aesthetics
description: Use when building or restyling ImIn frontend UI (React + Vite + Tailwind v4). Pushes for distinctive, intentional visual design and flags the common "AI-generated UI" tells (generic spacing, default fonts, low-contrast choices, cookie-cutter card layouts) before code ships.
---

# Frontend Aesthetics

Approach UI work as the design lead at a small studio known for giving every
project a visual identity that couldn't be mistaken for anyone else's. Make
deliberate, opinionated choices about palette, typography, and layout that
are specific to ImIn's actual content and users — not whatever a generic
prompt would produce — and take one real aesthetic risk you can justify.

This skill adapts Anthropic's official `frontend-design` Claude Code skill
(philosophy and process) plus a condensed, ImIn-stack-relevant subset of the
community `taste-skill` checklist (concrete anti-slop rules). Source
attribution at the bottom.

## Ground it in the subject

Before designing, pin down: what is this screen/component for, who is using
it (an ImIn user trying to find/host a meetup, on mobile or desktop), and
what is its single job. State that explicitly. Build with ImIn's real
content (event names, locations, dates, user-facing copy) — never lorem
ipsum or generic SaaS placeholder content — even in a draft pass.

## Design principles

**Typography carries the personality of the page.** Pair a display and body
face deliberately. Do not default to Inter/Roboto/Arial/system-ui as the
only typeface — ImIn's frontend already loads real fonts via Tailwind v4
CSS-first config (`frontend/src/index.css`); use that mechanism rather than
ad hoc inline styles. Set one consistent type scale (e.g. a defined ratio,
not arbitrary px values scattered across components) and keep weight/size
contrast intentional between headings and body text.

**Structure is information.** Numbering, dividers, eyebrows/labels should
encode something true about the content (a real sequence, a real category),
not decorate it. If a structural device doesn't carry meaning, cut it.

**Spacing must come from one scale.** Use Tailwind's spacing tokens
consistently (a single 4px-based scale) rather than mixing arbitrary
margins/padding values across components. Inconsistent spacing is one of
the most common "this was AI-generated" tells.

**Color must be deliberate and accessible.** Define a small named palette
(1 dominant + 1-2 accent colors) via Tailwind's theme, not picked ad hoc per
component. Check WCAG AA contrast (4.5:1) on all text and interactive
elements — especially buttons, form inputs, and focus states — as a
pass/fail gate, not an afterthought.

**Avoid the current AI-default looks** unless the content genuinely calls
for one: (1) warm cream background + high-contrast serif + terracotta
accent, (2) near-black background + single neon/acid accent, (3) generic
purple-gradient-on-white SaaS look, (4) three equal feature cards centered
on the page as the default answer to "show some features." These are
defaults, not choices — only use one if it's actually right for this
screen.

**Motion only with a purpose.** Justify each animation in one sentence
(state feedback, hierarchy, a deliberate page-load moment). No scattered
decorative hover effects, parallax, or infinite-loop micro-animations
without a reason. Respect `prefers-reduced-motion`.

**Match complexity to the vision.** A dense data view (e.g. the map/list of
events) needs precision in spacing and information hierarchy; a simple form
needs restraint. Elegance is executing the chosen direction well, not
adding more.

## Process

Work in two passes. First, sketch a compact plan: a short palette (named
hex values via Tailwind theme), the type pairing and scale, a one-sentence
layout concept, and the one signature element this screen will be
remembered by. Then review that plan: if any part of it is what a generic
prompt would produce for "a card list" or "a login form" rather than a
choice made for this specific ImIn screen, revise it and note what changed.
Only then write the code, following the revised plan.

Watch CSS/Tailwind class specificity when composing component-level classes
with section-level ones — conflicting padding/margin rules between a
`.section`-style wrapper and a button/card class is a common real bug
source, not just an aesthetic one.

## Pre-ship checklist

- [ ] One consistent accent color used the same way across the screen/flow
- [ ] One spacing scale (Tailwind defaults), no arbitrary one-off px values
- [ ] One font pairing, applied through Tailwind theme config, not inline
- [ ] WCAG AA contrast verified on body text, buttons, and form inputs/focus states
- [ ] No default Inter/Roboto/system-ui-only typography unless deliberately chosen
- [ ] No purple-gradient-on-white or "three equal cards" used reflexively
- [ ] Every animation justifiable in one sentence; `prefers-reduced-motion` respected
- [ ] Real ImIn content used in the draft, not lorem-ipsum/generic SaaS copy
- [ ] Responsive down to mobile; visible keyboard focus states present
- [ ] No layout shipped without a one-sentence rationale for why it fits this screen

If a box can't be honestly checked, the screen isn't done — fix it before
calling the work complete.

## Restraint

Spend boldness in one place per screen. Let one signature element be the
memorable thing; keep everything around it quiet and disciplined. Not
taking any risk is itself a risk — but scattering many small risks across
one screen reads as noise, not intent.

## Sources

- Anthropic, official `frontend-design` Claude Code skill —
  `anthropics/claude-code`, `plugins/frontend-design/skills/frontend-design/SKILL.md`
  (https://github.com/anthropics/claude-code/blob/main/plugins/frontend-design/skills/frontend-design/SKILL.md).
  Philosophy, process, and "AI-default looks" framing adapted from this
  source nearly verbatim.
- Community `taste-skill` project — `Leonxlnx/taste-skill`,
  `skills/taste-skill/SKILL.md` (https://github.com/Leonxlnx/taste-skill).
  Concrete pre-ship checklist items adapted and trimmed from this source's
  much larger checklist, keeping only what's relevant to ImIn's React +
  Vite + Tailwind v4 stack (no Next.js/RSC, GSAP, or multi-design-system
  guidance carried over, since ImIn doesn't use them).
