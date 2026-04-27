#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${CLAUNE_PACKAGE_NAME:-com.divyanshgolyan.claune.android}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_FILE="${1:-"$ROOT_DIR/android/build/projection-fixtures/latest-screen-state.json"}"

RUN_ID="${CLAUNE_RUN_ID:-}"
if [[ -z "$RUN_ID" ]]; then
  RUN_ID="$(
    adb shell "run-as $PACKAGE_NAME sh -c 'ls files/agent-runs 2>/dev/null | grep \"^run-\" | sort | tail -1'" |
      tr -d '\r'
  )"
fi

if [[ -z "$RUN_ID" ]]; then
  echo "No Claune run directory found on device for $PACKAGE_NAME." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUT_FILE")"
adb exec-out run-as "$PACKAGE_NAME" cat "files/agent-runs/$RUN_ID/latest-screen-state.json" > "$OUT_FILE"

echo "$OUT_FILE"
echo "Pulled latest-screen-state.json from $RUN_ID"
