#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="$ROOT_DIR/dist/fdroid-beta"
VERSION_NAME="$(awk -F'"' '/versionName[[:space:]]*=/{print $2; exit}' app/build.gradle.kts)"
VERSION_CODE="$(awk '/versionCode[[:space:]]*=/{print $3; exit}' app/build.gradle.kts)"

mkdir -p "$OUT_DIR"

echo "==> Building release APK (unsigned)"
./gradlew :app:assembleRelease

APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
if [[ ! -f "$APK_PATH" ]]; then
  APK_PATH="$(find app/build/outputs/apk/release -type f -name '*.apk' | head -n1 || true)"
fi

if [[ -z "${APK_PATH:-}" || ! -f "$APK_PATH" ]]; then
  echo "Release APK not found."
  exit 1
fi

TARGET_APK="$OUT_DIR/pdf-reader-${VERSION_NAME}-vc${VERSION_CODE}-fdroid.apk"
cp "$APK_PATH" "$TARGET_APK"

SHA256_FILE="$OUT_DIR/SHA256SUMS.txt"
if command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$TARGET_APK" > "$SHA256_FILE"
else
  sha256sum "$TARGET_APK" > "$SHA256_FILE"
fi

cat > "$OUT_DIR/RELEASE_NOTES.md" <<EOF
# PDF Reader ${VERSION_NAME} (Beta 1)

## Artifact
- $(basename "$TARGET_APK")

## Checksums
- See SHA256SUMS.txt

## Build info
- versionCode: ${VERSION_CODE}
- versionName: ${VERSION_NAME}
- Source branch: $(git rev-parse --abbrev-ref HEAD)
- Commit: $(git rev-parse --short HEAD)

## F-Droid prep
1. Open docs/FDROID_BETA_RELEASE.md
2. Use metadata template at fdroid/com.pdfreader.yml
3. Submit/update F-Droid metadata PR
EOF

echo "==> F-Droid beta package ready"
echo "Output directory: $OUT_DIR"
ls -lh "$OUT_DIR"
