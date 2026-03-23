#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUNS=5
SERIAL=""
APPLICATION_ID="com.pdfreader"
LAUNCH_ACTIVITY="com.pdfreader.MainActivity"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)
      RUNS="$2"
      shift 2
      ;;
    --serial)
      SERIAL="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--runs 5] [--serial DEVICE_ID]"
      exit 1
      ;;
  esac
done

echo "==> Building debug APK"
./scripts/launch-app.sh --build-only >/dev/null

ADB_BIN=""
if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
  ADB_BIN="${ANDROID_SDK_ROOT}/platform-tools/adb"
elif [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
  ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
elif [[ -f "local.properties" ]]; then
  SDK_DIR="$(grep '^sdk.dir=' local.properties | head -n 1 | cut -d'=' -f2- | sed 's#\\:#:#g')"
  if [[ -n "${SDK_DIR:-}" && -x "${SDK_DIR}/platform-tools/adb" ]]; then
    ADB_BIN="${SDK_DIR}/platform-tools/adb"
  fi
elif command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
fi

if [[ -z "$ADB_BIN" ]]; then
  echo "adb not found"
  exit 1
fi

if [[ -z "$SERIAL" ]]; then
  SERIAL="$($ADB_BIN devices | awk 'NR>1 && /device$/{print $1; exit}')"
fi

if [[ -z "$SERIAL" ]]; then
  echo "No online device/emulator found."
  exit 1
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo "==> Installing APK on $SERIAL"
"$ADB_BIN" -s "$SERIAL" install -r "$APK_PATH" >/dev/null

echo "==> Running startup timing smoke test ($RUNS runs)"

declare -a totals
for ((i=1; i<=RUNS; i++)); do
  "$ADB_BIN" -s "$SERIAL" shell am force-stop "$APPLICATION_ID" >/dev/null
  output="$($ADB_BIN -s "$SERIAL" shell am start -W -n "${APPLICATION_ID}/${LAUNCH_ACTIVITY}")"
  total_ms="$(echo "$output" | awk -F': ' '/TotalTime/{print $2}' | tr -d '\r')"
  totals+=("$total_ms")
  echo "Run $i: TotalTime=${total_ms}ms"
done

avg="$(printf '%s
' "${totals[@]}" | awk '{sum+=$1} END {if (NR>0) printf "%.2f", sum/NR; else print "0"}')"
max="$(printf '%s
' "${totals[@]}" | awk 'BEGIN{m=0} {if($1>m)m=$1} END{print m}')"
min="$(printf '%s
' "${totals[@]}" | awk 'BEGIN{m=999999} {if($1<m)m=$1} END{if(m==999999)m=0; print m}')"

echo "==> Startup summary"
echo "Average TotalTime: ${avg}ms"
echo "Min TotalTime: ${min}ms"
echo "Max TotalTime: ${max}ms"
