#!/usr/bin/env bash
set -euo pipefail
REMOTE_URL="${1:?Usage: $0 <github-repository-url>}"
git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE_URL"
git branch -M main
git push -u origin main
git tag -f v1.0.0-rc16
git push -f origin v1.0.0-rc16
