---
description: Launch the ImIn backend (Spring Boot, Maven) and frontend (Vite/React) dev servers for local development.
---

# Running ImIn locally

Monorepo: `backend/` (Spring Boot, Java 25, Maven) and `frontend/` (Vite + React + TypeScript).

## Backend

Needs a reachable Postgres connection and `JWT_SECRET` (see `backend/.env.example`). From `backend/`:

```
./mvnw spring-boot:run
```

Listens on `http://localhost:8080` by default.

## Frontend

From `frontend/`:

```
npm run dev
```

Serves on `http://localhost:5173` by default.

## Both at once

Run each command in its own terminal/background process — they're independent, no shared build step.
