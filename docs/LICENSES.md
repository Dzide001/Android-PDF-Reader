# Licenses

This project and its components are governed by the following licenses:

## Project License: AGPL v3

The PDF Reader application is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).

See [LICENSE](../LICENSE) for the full text.

### What this means:
- ✅ You can use, modify, and distribute the software
- ✅ You must provide source code if you distribute it or modify it
- ✅ You must disclose modifications made
- ✅ Network access counts as distribution (AGPL's network clause)
- ❌ You cannot use this in proprietary/closed-source products without separate licensing

---

## Component Licenses

### MuPDF (PDF Rendering Engine)
- **License:** AGPL v3
- **Repository:** https://mupdf.com/
- **Usage:** Native library integrated via JNI
- **Compliance:** Our AGPL license is compatible

### Tesseract (OCR Engine, Phase C)
- **License:** Apache 2.0
- **Repository:** https://github.com/UB-Mannheim/tesseract/wiki
- **Usage:** Optional OCR functionality
- **Compliance:** Apache 2.0 compatible with AGPL

### Android Framework
- **License:** Apache 2.0
- **Repository:** https://source.android.com/
- **Usage:** Base Android platform APIs

### Jetpack / AndroidX Libraries
- **License:** Apache 2.0
- **Repository:** https://source.android.com/
- **Included:**
  - Compose UI Framework
  - Room Database
  - WorkManager
  - Navigation
  - Lifecycle

### Kotlin
- **License:** Apache 2.0
- **Repository:** https://kotlinlang.org/
- **Usage:** Primary language for the app

### Gradle Build System
- **License:** Apache 2.0
- **Repository:** https://gradle.org/

---

## Third-Party Licenses

When adding new dependencies, verify their licenses are compatible with AGPL v3.

### License Compatibility Matrix

| License | Compatible with AGPL v3? | Notes |
|---------|---------------------------|-------|
| Apache 2.0 | ✅ Yes | Permissive, compatible |
| MIT | ✅ Yes | Permissive, compatible |
| GPL v2 | ⚠️ Check | May have restrictions |
| GPL v3 | ✅ Yes | Copyleft compatible |
| BSD | ✅ Yes | Permissive, compatible |
| ISC | ✅ Yes | Permissive, compatible |
| LGPL | ⚠️ Check | Weak copyleft, usually OK |
| Proprietary | ❌ No | Must acquire separate license |
| SSPL | ❌ No | Service provider clause conflict |

---

## Contributing & Licensing

By contributing to this project, you agree that:
1. Your contributions will be licensed under AGPL v3
2. You have the right to grant this license
3. You are not submitting code under a different license

Contributors may optionally sign a CLA (Contributor License Agreement) for additional clarity.

---

## Commercial Use

If you need to use this software in a proprietary/closed-source product:
1. **Option A:** Keep your modifications open under AGPL (distribute source)
2. **Option B:** Obtain a commercial license from the project maintainers

For commercial licensing inquiries, contact: [contact method TBD]

---

## Compliance Verification

Before release, verify compliance:
- [ ] All source files include AGPL header notice
- [ ] LICENSE file included in distribution
- [ ] NOTICE file lists all components and licenses
- [ ] Dependencies' licenses are documented
- [ ] Dependency source code made available

---

**Last Updated:** March 21, 2026
