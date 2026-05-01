#!/usr/bin/env sh

# Source this file from the repo root to configure the LangSmith CLI:
#   . scripts/langsmith-env.sh

_claune_langsmith_prop() {
  awk -F= -v key="$1" '$1 == key { print substr($0, length(key) + 2) }' android/local.properties
}

if [ ! -f android/local.properties ]; then
  echo "android/local.properties not found; run this from the repo root." >&2
  return 1 2>/dev/null || exit 1
fi

LANGSMITH_API_KEY="$(_claune_langsmith_prop claune.langsmith.apiKey)"
LANGSMITH_PROJECT="$(_claune_langsmith_prop claune.langsmith.project)"
LANGSMITH_ENDPOINT="$(_claune_langsmith_prop claune.langsmith.apiUrl)"

if [ -z "$LANGSMITH_API_KEY" ] || [ -z "$LANGSMITH_PROJECT" ] || [ -z "$LANGSMITH_ENDPOINT" ]; then
  echo "Missing one or more LangSmith properties in android/local.properties." >&2
  echo "Required: claune.langsmith.apiKey, claune.langsmith.project, claune.langsmith.apiUrl" >&2
  return 1 2>/dev/null || exit 1
fi

export LANGSMITH_API_KEY
export LANGSMITH_PROJECT
export LANGSMITH_ENDPOINT

unset -f _claune_langsmith_prop
