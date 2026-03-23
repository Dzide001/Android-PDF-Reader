# F-Droid Beta 1 Release Guide

This project uses AGPL-compatible dependencies and can be distributed on F-Droid.

## 1) Build release artifact

Run:

./scripts/release-beta-fdroid.sh

Output artifacts are generated under:
- dist/fdroid-beta/

## 2) Metadata template

Use/update:
- fdroid/com.pdfreader.yml

Key fields to keep updated for each release:
- CurrentVersion
- CurrentVersionCode
- commit / tag
- gradle build command

## 3) Prepare source reference

For F-Droid reproducibility:
- Push all release changes to main
- Tag release commit (example): v0.1.0-beta1

## 4) Submit to F-Droid data repo

Open a PR against fdroiddata with:
- metadata/com.pdfreader.yml (or update existing)
- release version details

## 5) Verify locally

- Build release APK: ./gradlew :app:assembleRelease
- Verify checksums from dist/fdroid-beta/SHA256SUMS.txt

## 6) Beta acceptance checks

- App launches and opens PDF
- Page mode + continuous scroll mode work
- Text extraction + copy works
- Highlight mode works
- Night mode works
- Tablet layout adapts correctly

