#!/usr/bin/env bash
set -euo pipefail

HOST="${CHENGDU_HOST:?set CHENGDU_HOST=user@host}"
DIR="${CHENGDU_PATH:-/var/www/raster-dial}"
APK="${1:?usage: tools/sync_chengdu.sh path/to/raster-dial-X-debug.apk}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$ROOT/update.json"

[[ -f "$JSON" ]] || { echo "missing $JSON; run tools/publish_update.py first" >&2; exit 1; }
[[ -f "$APK" ]] || { echo "missing APK: $APK" >&2; exit 1; }

ssh "$HOST" "mkdir -p '$DIR'"
scp "$JSON" "$APK" "$HOST:$DIR/"
echo "synced update.json + $(basename "$APK") -> $HOST:$DIR"
