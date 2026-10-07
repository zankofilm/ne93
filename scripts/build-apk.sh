#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
  echo "ERROR: ANDROID_HOME یا ANDROID_SDK_ROOT تنظیم نشده است." >&2
  exit 2
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle در PATH پیدا نشد. پروژه را با Android Studio باز کنید یا Gradle 8.9 نصب کنید." >&2
  exit 3
fi
gradle --version
gradle :app:assembleDebug
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "ERROR: APK ساخته نشد." >&2; exit 4; }
cp "$APK" "$ROOT/Madreseyar-Student-1.3.0-debug.apk"
echo "OK: $ROOT/Madreseyar-Student-1.3.0-debug.apk"
