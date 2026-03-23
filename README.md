# PDF Reader - Open Source PDF Editor for Android

A high-performance, open-source PDF reader and editor for Android phones and tablets, built with Kotlin and Jetpack Compose.

**License:** AGPL v3 | **Status:** Beta 2 (Phase B complete)

---

## Features (Current & Planned)

### ✅ Phase A - MVP (Complete)

- Fast PDF rendering with MuPDF
- Page navigation (swipe, buttons)
- Zoom and scroll functionality
- Text selection and copy to clipboard
- Basic highlight annotations
- Local file library with database
- Night mode support
- Tablet-responsive UI

### ✅ Phase B - Advanced Annotations (Complete)

- Low-latency stylus drawing with pressure sensitivity
- Shape tools (rectangles, circles, arrows)
- Eraser tool
- Dual-pane tablet layout
- Page management (reorder, delete, rotate)
- Form filling support
- Digital signatures

### 🔮 Phase C - OCR & Search (Planned)
- Offline OCR with Tesseract
- Full-text search with FTS5
- OCR text export

### 🚀 Phase D - AI & Cloud (Optional)
- On-device AI (llama.cpp)
- Cloud sync (WebDAV, Google Drive, Dropbox)
- PDF conversion utilities

---

## Quick Start

### Requirements
- Android Studio 2023.1+
- JDK 11+
- Android SDK 34
- NDK 26.2+
- CMake 3.22+

### Setup Development Environment
```bash
# Clone repository with submodules
git clone --recursive https://github.com/your-org/pdf-reader.git
cd pdf-reader

# Run setup script
./scripts/setup-dev-env.sh

# Build project
./gradlew build
```

### Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

---

## Project Structure

```
root/
├── app/                    # Main Android app (Compose UI)
├── core/
│   ├── pdf-engine/        # JNI + MuPDF wrapper
│   ├── renderer/          # Rendering controller
│   ├── annotations/       # Annotation models
│   ├── ocr/              # OCR integration
│   └── storage/          # Database layer
├── native/               # Native code (C++, MuPDF)
├── scripts/              # Build utilities
├── docs/                 # Documentation
│   ├── ARCHITECTURE.md   # Architecture decisions
│   └── LICENSES.md       # License information
└── PROJECT_ROADMAP.md    # Feature roadmap & milestones
```

---

## Architecture

### High-Level Design
- **UI Layer:** Jetpack Compose + Material 3
- **Rendering:** SurfaceView for PDF + TextureView for annotations
- **Engine:** MuPDF (native) via JNI bridge
- **Data:** Room database + WorkManager for background tasks

### Key Technical Decisions
See [Architecture Decisions](docs/ARCHITECTURE.md) for detailed rationale on:
- MuPDF selection for rendering
- TextureView for annotation overlay
- Separate annotation storage model
- SQLite FTS5 for search
- AGPL v3 licensing

---

## Performance Targets

| Metric | Target |
|--------|--------|
| First page render | < 300 ms |
| Page switch | < 100 ms |
| Zoom response | < 16 ms/frame (60 FPS) |
| Stylus latency | < 15 ms |
| App cold start | < 2 sec |
| Search (1000pp PDF) | < 500 ms |

---

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for:
- Code style guidelines
- Pull request process
- Testing requirements
- Commit message format

### Development Workflow
1. Fork the repository
2. Create a feature branch (`feature/your-feature`)
3. Make changes with tests
4. Submit PR with detailed description
5. Address review feedback

---

## Building from Source

### Prerequisites
```bash
# macOS
brew install cmake openjdk@11

# Linux (Ubuntu)
sudo apt-get install cmake openjdk-11-jdk-headless

# Windows
# Use Android Studio to install dependencies
```

### Build Steps
```bash
# Full build (debug)
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# With native debugging
./gradlew assembleDebug -Djava.library.path=...

# Prepare Beta 2 F-Droid package
./scripts/release-beta-fdroid.sh
```

F-Droid release docs and metadata:
- docs/FDROID_BETA_RELEASE.md
- fdroid/com.pdfreader.yml

---

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests (Android devices)
```bash
./gradlew connectedAndroidTest
```

### Performance Tests
```bash
# Profile with CPU/Memory profiler in Android Studio
# Or use: ./gradlew connectedCheck --no-daemon

# Quick startup timing smoke test
./scripts/perf-smoke.sh --runs 5
```

---

## Dependencies

### Core Libraries
- **MuPDF** (v1.24.2) - PDF rendering
- **Tesseract** (v5.3.1) - OCR (Phase C)
- **Jetpack Compose** - UI framework
- **Room** - Database
- **WorkManager** - Background tasks
- **Kotlin Coroutines** - Async programming

See [LICENSES.md](docs/LICENSES.md) for complete dependency information.

---

## FAQ

**Q: Why AGPL v3?**  
A: MuPDF uses AGPL, so we must too. This ensures the community benefits from all improvements.

**Q: Can I use this commercially?**  
A: Yes, with two options:
1. Keep modifications open-source (AGPL compliance)
2. Obtain commercial license from maintainers

**Q: Why MuPDF over PDFium?**  
A: MuPDF is lighter, faster on mobile, and AGPL-licensed. See [Architecture Decisions](docs/ARCHITECTURE.md#adr-001-mupdf-as-primary-rendering-engine).

**Q: Will this work on tablets?**  
A: Yes! Responsive UI designed for 6" phones to 12"+ tablets. Dual-pane layout in Phase B.

**Q: Can I contribute?**  
A: Absolutely! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## Roadmap

- **Week 1-2:** MuPDF JNI bridge + basic rendering
- **Week 3-4:** Page navigation + zoom + text selection
- **Week 5-6:** Basic annotations (highlight, notes)
- **Week 7-8:** Library + database + testing
- **Weeks 9-12:** Advanced stylus, tablet UI, performance tuning
- **Beta 2:** Release on F-Droid
- **Weeks 13-20:** OCR + search (Phase C)
- **Weeks 21-24:** AI + cloud (Phase D)

See [PROJECT_ROADMAP.md](PROJECT_ROADMAP.md) for detailed milestones.

---

## License

This project is licensed under **GNU Affero General Public License v3.0**.

- **Source code:** Full transparency required
- **Network clause:** Using this as a service requires source disclosure
- **Commercial:** Possible with separate commercial licensing

See [LICENSE](LICENSE) and [docs/LICENSES.md](docs/LICENSES.md) for details.

---

## Contact & Support

- **Issues:** Report bugs on GitHub Issues
- **Discussions:** Ask questions in GitHub Discussions
- **Security:** Report vulnerabilities privately (TBD)
- **Contributing:** See [CONTRIBUTING.md](CONTRIBUTING.md)

---

## Credits

Built with:
- [MuPDF](https://mupdf.com/) - PDF rendering engine
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern Android UI
- [Android Open Source Project](https://source.android.com/) - Android framework
- Community contributors ❤️

---

## Changelog

### v0.1.0-alpha (In Progress)
- Initial project setup
- Basic MuPDF JNI bridge (skeleton)
- Gradle build configuration
- CI/CD pipeline (GitHub Actions)
- Architecture decisions documented

---

**Last Updated:** March 21, 2026  
**Status:** 🏗️ Under Active Development
