# ImIn — Multi-Agent Spec-Driven Development Harness

This repository builds a product using a spec-driven, multi-agent pipeline
run through Claude Code subagents. This file configures the harness; the
product itself is defined by specs under `specs/`.

## Stack

Monorepo with two independently deployed apps:

- `backend/` — Java 25, Spring Boot 4.1 (Maven, `./mvnw`), Spring Security
  with app-issued JWTs (HMAC, see `JwtConfig`/`JwtService`) plus OAuth2 login
  (Google) that also resolves to a JWT. Postgres via Spring Data JPA — meant
  to point at a Neon Postgres instance (free tier, via Vercel Marketplace or
  neon.tech directly; reachable from anywhere, not tied to Vercel hosting).
  Deploys to **Render** (free tier) via `backend/Dockerfile` + `render.yaml`
  — Vercel cannot run a persistent JVM server, so it's not an option for
  this half of the stack.
- `frontend/` — TypeScript + React (Vite), Tailwind CSS v4
  (`@tailwindcss/vite`, CSS-first config in `src/index.css`), Leaflet via
  `react-leaflet` (see `src/components/MapView.tsx`). Deploys to **Vercel**.

Env var contracts live in `backend/.env.example` and `frontend/.env.example`
— never commit real secrets, only `.env.example` placeholders.

## Session rule: always route through Leader

For every user prompt in this project, do not answer or act directly. Invoke
the `leader` subagent (Agent tool, `subagent_type: leader`) with the user's
request, and let it triage and delegate. Surface the Leader's outcome back to
the user. Do not skip this even for requests that look simple — Leader decides
what's trivial, not the top-level session.

## Pipeline roles

Defined under `.claude/agents/`:

| Agent | Role |
|---|---|
| `leader.md` | Receives every request, decides which role(s) to invoke and in what order, tracks spec status, reports results. |
| `analyst.md` | Turns requests into specs (problem, requirements, acceptance criteria) under `specs/`. |
| `architect.md` | Turns an approved spec into a technical design (interfaces, file/module layout, data flow). |
| `implementer.md` | Writes code against a spec + design. |
| `reviewer.md` | Reviews an implementation against its spec/design for correctness and quality. |
| `tester.md` | Writes/runs tests that verify a spec's acceptance criteria. |

## Spec lifecycle

Specs live at `specs/<slug>/spec.md` (template: `specs/_TEMPLATE.md`) and move
through: `draft -> approved -> in-progress -> implemented -> verified`. Leader
keeps each spec's `status` field current as work moves through the pipeline.
