# Deployment

## Branching model

- `master` — production. Every push auto-deploys backend (Render) and
  frontend (Vercel). Note: this repo's default/production branch is named
  **`master`**, not `main`. Keep it as-is for now (renaming later is a
  deliberate, separate action that also requires updating branch settings
  in both the Render and Vercel dashboards).
- `develop` — staging / in-progress work not meant to go live in production.
  - Vercel auto-generates a preview deployment per non-production branch by
    default, so `develop` gets one automatically once pushed — no extra
    Vercel config needed beyond confirming `master` is the Production
    Branch (see below).
  - Render staging is via the optional `imin-backend-develop` service
    defined in `render.yaml` (see comments there for setup caveats), or via
    Render's own Preview Environments feature if available on the account's
    plan — verify in the Render dashboard, this changes over time.

## Local development

See `.claude/skills/run-imin/SKILL.md` for exact commands to run the backend
and frontend locally.

Recommendation: don't point local dev at the production Neon database. Use a
separate Neon "branch" (Neon's built-in DB branching feature, e.g. a `dev`
branch of the Neon project) and put its connection string in a local,
git-ignored `backend/.env` or exported environment variables — never commit
real credentials.

## Manual dashboard steps

These cannot be verified or performed from the repo — they require logging
into each dashboard directly.

**Render**
- Confirm `imin-backend`'s auto-deploy branch is `master`
  (Settings > Build & Deploy).
- If adopting `imin-backend-develop`, either trigger a Blueprint re-sync or
  create the service manually pointed at `develop`.
- Add real values for `RESEND_API_KEY`, `EMAIL_FROM_ADDRESS`, and
  `ORS_API_KEY` (previously not declared in `render.yaml` at all).

**Vercel**
- Confirm Project Settings > Git > Production Branch is set to `master`
  (not the literal string `main`).
- Confirm preview deployments are enabled for other branches (default
  behavior).
- Optionally add `develop`-specific environment variables under
  Project Settings > Environment Variables, scoped to Preview.

**Neon**
- Create a `dev`/`develop` database branch (Neon's branching feature),
  distinct from production. Use its connection string for local dev and for
  the `imin-backend-develop` Render service.
