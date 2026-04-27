#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FIXTURE="${CLAUNE_PROJECTION_FIXTURE:-"$ROOT_DIR/android/build/projection-fixtures/latest-screen-state.json"}"
REPORT="${CLAUNE_PROJECTION_REPORT:-"$ROOT_DIR/android/build/reports/projection-benchmark.json"}"
ITERATIONS="${CLAUNE_PROJECTION_ITERATIONS:-20}"
WARMUP="${CLAUNE_PROJECTION_WARMUP:-3}"

if [[ "${1:-}" == "--pull" || ! -s "$FIXTURE" ]]; then
  "$ROOT_DIR/scripts/pull_projection_fixture.sh" "$FIXTURE"
fi

cd "$ROOT_DIR/android"
./gradlew :app:testDebugUnitTest \
  --tests '*ScreenProjectionBenchmarkTest' \
  -Dclaune.projection.benchmark=true \
  -Dclaune.projection.fixture="$FIXTURE" \
  -Dclaune.projection.report="$REPORT" \
  -Dclaune.projection.iterations="$ITERATIONS" \
  -Dclaune.projection.warmup="$WARMUP"

echo "Projection benchmark report: $REPORT"
