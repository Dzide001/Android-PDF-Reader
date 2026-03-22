#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUILD_ONLY=false
VARIANT="Debug"
APPLICATION_ID="com.pdfreader"
LAUNCH_ACTIVITY="com.pdfreader.MainActivity"
VARIANT_LOWER="debug"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build-only)
      BUILD_ONLY=true
      shift
      ;;
    --variant)
      VARIANT="$2"
      shift 2
      ;;
    --application-id)
      APPLICATION_ID="$2"
      shift 2
      ;;
    --activity)
      LAUNCH_ACTIVITY="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--build-only] [--variant Debug] [--application-id com.pdfreader] [--activity com.pdfreader.MainActivity]"
      exit 1
      ;;
  esac
done

VARIANT_LOWER="$(printf '%s' "$VARIANT" | tr '[:upper:]' '[:lower:]')"

echo "==> Building app (${VARIANT})"
./gradlew ":app:assemble${VARIANT}"

echo "==> Build complete"

APK_PATH="app/build/outputs/apk/${VARIANT_LOWER}/app-${VARIANT_LOWER}.apk"
if [[ ! -f "$APK_PATH" ]]; then
  APK_PATH="$(find app/build/outputs/apk -type f -name "*.apk" | head -n 1 || true)"
fi

if [[ -z "${APK_PATH:-}" || ! -f "$APK_PATH" ]]; then
  echo "Unable to find built APK."
  exit 1
fi

if [[ "$BUILD_ONLY" == true ]]; then
  echo "Build-only mode enabled. APK: $APK_PATH"
  exit 0
fi

ADB_BIN=""
if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
  ADB_BIN="${ANDROID_SDK_ROOT}/platform-tools/adb"
elif [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
  ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
fi

if [[ -z "$ADB_BIN" ]]; then
  echo "adb not found. Install Android platform-tools or set ANDROID_SDK_ROOT."
  exit 1
fi

echo "==> Checking connected Android devices"
"$ADB_BIN" start-server >/dev/null
DEVICE_COUNT="$($ADB_BIN devices | awk 'NR>1 && /device$/{count++} END{print count+0}')"
if [[ "$DEVICE_COUNT" -eq 0 ]]; then
  echo "No online device/emulator found. Start an emulator or connect a device and try again."
  exit 1
fi

echo "==> Installing APK: $APK_PATH"
"$ADB_BIN" install -r "$APK_PATH"

echo "==> Launching app"
"$ADB_BIN" shell am start -n "${APPLICATION_ID}/${LAUNCH_ACTIVITY}" >/dev/null

echo "App launched: ${APPLICATION_ID}/${LAUNCH_ACTIVITY}"
