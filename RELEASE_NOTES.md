# Beta 3 Release Notes

**Release:** 0.3.0-beta3
**Status:** Release prep
**Date:** 2026-04-06

## Highlights

- Added offline OCR layout reconstruction from Tesseract hOCR output.
- Added FTS5 full-text indexing and in-app OCR search with snippets.
- Added plain-text and searchable PDF export for OCR content.
- Improved large-document OCR performance and memory handling.
- Refined PDF viewer interactions and control sizing.

## Included Phase C Work

- Background OCR with WorkManager
- hOCR parsing and layout persistence
- OCR search index and search UI
- OCR text export
- Searchable PDF export
- Large-PDF performance optimizations

## Validation

- Debug build passes.
- APK installs on device `50eb54c9`.
- App launches successfully after recent OCR/search changes.

## Notes

- Beta 3 is prepared for release; final tagging can happen after any last smoke-test pass.
- Phase D remains optional and starts with AI/cloud exploration.
