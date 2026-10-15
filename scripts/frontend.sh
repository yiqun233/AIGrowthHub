#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export PATH="$HOME/.volta/bin:$PATH"
cd "$ROOT/frontend"
exec npm run dev
