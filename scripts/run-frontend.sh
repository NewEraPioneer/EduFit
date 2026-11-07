#!/usr/bin/env bash
# =============================================================================
# EduFit Frontend Local Launcher (Bash)
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
BACKEND_URL="${1:-https://edufit-255v.onrender.com}"
PORT="${2:-3000}"

echo "================================================="
echo "       EduFit Frontend - Local Dev Launcher      "
echo "================================================="

ENV_LOCAL="$FRONTEND_DIR/.env.local"
if [[ ! -f "$ENV_LOCAL" ]]; then
  echo "[INFO] Creating $ENV_LOCAL with BACKEND_URL=$BACKEND_URL..."
  echo "BACKEND_URL=$BACKEND_URL" > "$ENV_LOCAL"
fi

cd "$FRONTEND_DIR"
npm run dev -- -p "$PORT"
