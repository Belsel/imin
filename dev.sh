#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

tmp_backend=/tmp/backend.log
tmp_frontend=/tmp/frontend.log

# Start the database container
docker start imin-db

# Export backend environment variables
export DATABASE_URL=jdbc:postgresql://localhost:5432/imin
export DATABASE_USERNAME=postgres
export DATABASE_PASSWORD=postgres
export JWT_SECRET=change-me-to-a-long-random-value
export GOOGLE_CLIENT_ID=placeholder-not-a-real-client-id
export GOOGLE_CLIENT_SECRET=placeholder-not-a-real-client-secret
export FRONTEND_URL=http://localhost:5173
export RESEND_API_KEY=
export EMAIL_FROM_ADDRESS=no-reply@example.com
export ORS_API_KEY=
export PORT=8080

# Start the backend in the background
(cd "$SCRIPT_DIR/backend" && ./mvnw.cmd spring-boot:run) >"$tmp_backend" 2>&1 &
BACKEND_PID=$!

# Start the frontend in the background
(cd "$SCRIPT_DIR/frontend" && npm run dev) >"$tmp_frontend" 2>&1 &
FRONTEND_PID=$!

# Kill all background jobs on exit
trap 'echo "Shutting down..."; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; wait 2>/dev/null' INT TERM

echo ""
echo "Backend and frontend are starting up."
echo "Tailing logs from $tmp_backend and $tmp_frontend ..."
echo ""

tail -f "$tmp_backend" "$tmp_frontend"
