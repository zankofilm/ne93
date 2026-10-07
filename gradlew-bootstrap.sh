#!/usr/bin/env bash
set -euo pipefail
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
echo "Gradle is not installed. Open the project in Android Studio or install Gradle 8.9, then run: gradle $*" >&2
exit 3
