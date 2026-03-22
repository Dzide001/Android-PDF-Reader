# Open-Source PDF Reader + Editor for Android
**Architectural Blueprint & Execution Roadmap**

**Project Status:** Planning → Development  
**Last Updated:** March 21, 2026  
**License:** AGPL v3

---

## Executive Summary

This document defines the architecture, execution roadmap, and critical technical decisions for building a high-performance, open-source PDF reader and editor for Android phones and tablets. The project prioritizes:
- **Performance:** Stylus latency < 15ms, page render < 300ms
- **Open-source compliance:** AGPL v3, full transparency
- **Modularity:** Clear separation between rendering engine, UI, and annotation layers

---

## Table of Contents
1. [Licensing & Open-Source Strategy](#1-licensing--open-source-strategy)
2. [Core Architecture](#2-core-architecture)
3. [Feature Roadmap](#3-feature-roadmap-phased-execution)
4. [Technical Stack](#4-technical-stack-summary)
5. [Critical Risks & Mitigations](#5-critical-risks--mitigations)
6. [Development Approach](#6-development-approach)
7. [Technical Specifications](#7-technical-specifications)
8. [Repository Structure](#8-repository-structure)
9. [Performance Targets](#9-performance-targets-quantified)
10. [CI/CD Pipeline](#10-cicd-pipeline)
11. [Testing Strategy](#11-testing-strategy)
12. [Execution Timeline](#12-final-execution-order)
13. [Milestone Tracking](#13-milestone-tracking)

---

## 1. Licensing & Open-Source Strategy

### License Decision
- **Base engine:** [MuPDF](https://mupdf.com/) (AGPL)
- **App license:** AGPL v3 (source code fully available)
- **Distribution:** Google Play, F-Droid, GitHub Releases

### Monetisation (Future)
- Voluntary donations / sponsors
- Optional cloud services (with separate TOS)
- Commercial licensing if proprietary version is pursued

### Rationale
AGPL ensures legal compatibility with MuPDF without commercial fees. Open-source accelerates community feedback, security audits, and contributions.

---

## 2. Core Architecture

### High-Level System Design

```
┌─────────────────────────────────────────┐
│          Kotlin / Jetpack Compose       │  ← UI for menus, dialogs, library
├─────────────────────────────────────────┤
│          Custom View Layer              │
│  ┌─────────────────┬─────────────────┐  │
│  │ SurfaceView     │ TextureView      │  │
│  │ (PDF rendering) │ (Annotations)    │  │
│  └─────────────────┴─────────────────┘  │
├─────────────────────────────────────────┤
│          JNI Bridge (C++ / Kotlin)      │
├─────────────────────────────────────────┤
│          MuPDF Engine (C)               │
└─────────────────────────────────────────┘
```

### Rendering Architecture

#### SurfaceView Layer (PDF)
- Dedicated `SurfaceView` for page rendering
- MuPDF renders directly to RGBA buffer
- Optimal for performance, minimizes copying

#### TextureView Layer (Annotations)
- Overlayed for annotation and stylus input
- Allows alpha blending and transparency
- Solves synchronization issues with dual SurfaceViews

#### Stylus Pipeline
1. Collect `MotionEvent` with historical points
2. Interpolate using Catmull-Rom splines
3. Render strokes directly with GPU-accelerated canvas
4. Support pressure and tilt via Android APIs
5. **Palm rejection:** State machine (ignore `TOOL_TYPE_FINGER` while stylus active)
6. Optional Samsung S Pen SDK integration for supported devices

---

## 3. Feature Roadmap (Phased Execution)

### Phase A – MVP (8–12 weeks)
**Goal:** Solid, fast reader with basic annotations

#### Deliverables
- ✅ MuPDF integration
  - Load PDFs from storage (SAF + local cache)
  - Page navigation, zoom, table of contents
  - **Palm rejection** (active from day one)
- ✅ Basic annotations
  - Highlight, underline, strikeout (MuPDF capabilities)
  - Sticky notes (text pop-ups)
- ✅ Text selection
  - Character-level selection using MuPDF bounding boxes
  - Copy to clipboard
- ✅ Library
  - Local database (Room) for recent files, favourites, metadata
  - Basic file browser using `MediaStore` (documents)
- ✅ Night mode (colour inversion)
- ✅ Responsive UI (tablet-aware, no phone-only scaling)

**Success Criteria:**
- First page renders in < 300ms
- App opens recent PDFs in < 1 second
- Stylus latency < 50ms

---

### Phase B – Advanced Annotation & Document Manipulation (8–12 weeks)
**Goal:** Feature-complete annotation and page management

#### Deliverables
- ✅ Stylus-optimised drawing
  - Freehand ink with pressure sensitivity
  - Shape tools (rectangles, circles, arrows)
  - Eraser (stroke or pixel)
  - Low-latency rendering on annotation overlay
- ✅ Tablet UI
  - Dual-pane layout: document list + viewer
  - Toolbar customisation (floating or side-dock)
  - Keyboard shortcuts (external keyboard support)
- ✅ Page management
  - Reorder pages (thumbnail grid)
  - Delete, rotate, insert (camera, gallery, blank)
- ✅ Form filling (AcroForms)
  - Text fields, checkboxes, radio buttons
- ✅ Signatures
  - Create, save, place signatures (stored locally)

**Success Criteria:**
- Stylus latency < 15ms (GPU-accelerated)
- Page reordering smooth on 500+ page PDFs
- Form filling with auto-detection of fields

---

### Phase C – OCR & Search (12–16 weeks)
**Goal:** Offline OCR and full-text search

#### Deliverables
- ✅ OCR engine
  - **Tesseract** integrated via native library
  - Background processing using `WorkManager`
  - User-initiated OCR on scanned documents
- ✅ Layout reconstruction
  - Use hOCR output from Tesseract
  - Rebuild paragraphs and reading order
  - Store OCR results as searchable layer
- ✅ Full-text search
  - Index OCR text and existing PDF text
  - Fast search with snippet preview
  - Highlight search results in document
- ✅ Export OCR text
  - Save as plain text or PDF with selectable text

**Success Criteria:**
- OCR per page < 4 seconds
- Search response < 500ms on 1000-page OCR'd PDF

---

### Phase D – AI & Cloud Sync (Optional, 8–12 weeks)
**Goal:** Differentiating features, privacy-first

#### Deliverables
- ✅ On-device AI (optional)
  - **llama.cpp** integration for summarisation and Q&A
  - Quantized models ≤4GB RAM
- ✅ Cloud sync
  - WebDAV (Nextcloud, ownCloud)
  - Google Drive / Dropbox integration
  - Auto-sync annotations and reading progress
- ✅ Cloud fallback AI
  - User-supplied API keys (OpenAI, Anthropic)
  - No data sent without explicit consent
- ✅ PDF conversion
  - Convert to/from Office formats

**Success Criteria:**
- Summary generation < 10 seconds for 50-page PDF
- Cloud sync latency < 2 seconds for annotation changes

---

## 4. Technical Stack (Summary)

| Layer               | Technology                                      |
|---------------------|-------------------------------------------------|
| Language            | Kotlin (with C++ for engine)                    |
| UI Framework        | Jetpack Compose (for controls) + custom views   |
| Rendering Engine    | MuPDF (C, via JNI)                              |
| Annotation Drawing  | Custom `TextureView` with `Canvas` / OpenGL     |
| Database            | Room (SQLite) + FTS5 (full-text search)         |
| Background Jobs     | WorkManager, Coroutines                         |
| OCR                 | Tesseract (native, with custom layout analysis) |
| File Access         | SAF + MediaStore + custom index                 |
| Stylus Support      | AndroidX `MotionEvent` + vendor SDK (optional)  |
| AI (on-device)      | `llama.cpp` (C++), quantised GGUF models        |
| Minimum API Level   | Android 8 (API 26)                              |

---

## 5. Critical Risks & Mitigations

| Risk                      | Impact | Mitigation                                                                 |
|---------------------------|--------|----------------------------------------------------------------------------|
| **MuPDF JNI complexity**  | High   | Create dedicated module for JNI bindings; start with minimal functions.    |
| **Stylus latency**        | High   | Use separate `TextureView` for ink; render strokes directly on GPU thread. |
| **OCR layout accuracy**   | Medium | Ship with usable but imperfect layout; improve iteratively.               |
| **Text selection issues** | High   | Accept character-level selection for v1; add column-aware logic later.    |
| **Android fragmentation** | Medium | Target API 26+ (Android 8) initially; use `androidx` compatibility.       |
| **AGPL compliance**       | Low    | Publish source from day one; include clear licensing notices.             |
| **Large file performance**| Medium | Implement tile-based rendering + LRU cache; stress test with 500MB PDFs.  |
| **Memory leaks (JNI)**    | Medium | Rigorous native code review; use AddressSanitizer in CI.                  |

---

## 6. Development Approach

### Source Control & Collaboration
- **Repository:** GitHub with AGPL license
- **Branch strategy:** Main branch stable, feature branches for development
- **Contribution guidelines:** Clear CONTRIBUTING.md for community

### Architecture Principles
- **Modularity:** Engine module (MuPDF + JNI) separate from UI
- **Separation of concerns:** Rendering, annotation, and UI layers independent
- **Reusability:** Engine module can be used in other projects

### Code Quality
- **Testing:** Unit tests for JNI bindings, instrumented tests for rendering
- **Linting:** Kotlin linter (ktlint) + Android Lint
- **Code review:** All PRs require review before merge
- **Documentation:** API docs for JNI bridge, architecture ADRs

### Community Engagement
- **Early beta:** GitHub Releases for testing builds
- **Feedback:** Issues and discussions for feature requests
- **Transparency:** Public roadmap and milestone tracking

---

## 7. Technical Specifications

### 7.1 MuPDF Integration Mode

#### Rendering Flow
```
fz_new_context
  → fz_open_document
    → fz_load_page
      → fz_new_pixmap_from_page_contents
        → render into RGBA buffer
          → pass to ANativeWindow (direct, no Java copying)
```

#### Critical Constraint
- **Avoid Java bitmap copying** (performance bottleneck)
- Use **direct buffer → Surface** approach

---

### 7.2 Annotation Persistence Model

Store annotations separately from PDF in v1

```kotlin
Annotation {
    docId: String
    page: Int
    type: AnnotationType  // highlight, underline, strikeout, ink, shape
    geometry: List<Point>  // points for ink, rects for shapes
    color: Int
    strokeWidth: Float
    timestamp: Long
    uuid: String
}
```

#### Rationale
- Avoid rewriting PDF on every change
- Faster iteration and undo/redo
- Safer user experience
- Later: export → flatten into PDF via MuPDF

---

### 7.3 Rendering Pipeline (Final Form)

```
UI Thread:
    viewport change / zoom / page navigation
      → request render

Render Thread:
    compute visible region
      → determine needed tiles
        → call MuPDF for each tile
          → cache result
            → composite on Surface

Cache:
    LRU tile cache (key = page + zoom level + tile index)
    Memory limit: 256MB (configurable)
```

#### Tile-Based Rendering Benefits
- Smooth zooming without re-rendering entire page
- Better memory usage on large PDFs
- Faster response to viewport changes

---

### 7.4 Stylus Engine (GPU-Accelerated)

#### Drawing Backend
- **Preferred:** OpenGL ES 3.0 or Vulkan (long-term)
- **Fallback:** Android Canvas (initial implementation)

#### Stroke Model
```kotlin
Stroke {
    id: String
    points: List<Point>  // x, y, timestamp
    pressure: List<Float>
    tilt: List<Float>
    color: Int
    width: Float
    tool: Stylus | Finger | Eraser
}
```

#### Interpolation
- **Catmull-Rom splines** (better than cubic for real-time input)
- Smooth curves with responsive feedback

#### Palm Rejection
- State machine: active when stylus detected
- Ignore `TOOL_TYPE_FINGER` events during stylus input
- Configurable sensitivity per device

---

### 7.5 OCR Storage Strategy (Final Decision)

**Do NOT modify original PDF in Phase C**

```kotlin
OCRIndex {
    docId: String
    page: Int
    text: String  // full OCR'd text for page
    boundingBoxes: List<BoundingBox>  // for highlighting
    language: String
    confidence: Float
    timestamp: Long
}
```

#### Search Implementation
- SQLite FTS5 (Full-Text Search) index
- Fast queries: < 500ms on 1000-page document

#### Benefits
- Instant search without file modification
- No corruption risk to original PDF
- Easy iteration and improvement

---

## 8. Repository Structure

```
root/
├── app/                        # Android app (Compose UI)
│   ├── src/main/kotlin/
│   │   ├── ui/                # Compose screens
│   │   ├── features/          # Feature screens (viewer, library, etc.)
│   │   ├── data/              # Data layer, repositories
│   │   └── MainActivity.kt
│   └── build.gradle.kts
│
├── core/                       # Shared modules
│   ├── pdf-engine/            # JNI + MuPDF wrapper
│   │   ├── src/main/kotlin/
│   │   ├── src/main/cpp/      # C++ JNI bridge
│   │   └── build.gradle.kts
│   ├── renderer/              # Rendering controller
│   ├── annotations/           # Annotation models + logic
│   ├── ocr/                   # Tesseract integration
│   └── storage/               # Room + file index
│
├── native/                     # Native dependencies
│   ├── mupdf/                 # MuPDF source (git submodule)
│   ├── tesseract/             # OCR engine (git submodule)
│   ├── jni/                   # C++ bridge code
│   └── CMakeLists.txt
│
├── scripts/                    # Build and utility scripts
│   ├── build-mupdf.sh
│   └── setup-native.sh
│
├── docs/                       # Documentation
│   ├── ARCHITECTURE.md
│   ├── JNI_BRIDGE.md
│   ├── CONTRIBUTING.md
│   └── API.md
│
├── .github/
│   └── workflows/
│       ├── build.yml          # CI/CD pipeline
│       └── release.yml
│
├── build.gradle.kts           # Root build config
├── settings.gradle.kts
├── .gitignore
├── .gitmodules
├── LICENSE (AGPL v3)
├── README.md
└── PROJECT_ROADMAP.md (this file)
```

---

## 9. JNI Bridge (Minimum Interface)

### Kotlin API (Initial)

```kotlin
interface PdfEngine {
    // Document management
    fun openDocument(path: String): Long
    fun closeDocument(doc: Long)
    fun getPageCount(doc: Long): Int
    fun getPageDimensions(doc: Long, page: Int): PageDimensions
    
    // Rendering
    fun renderPage(
        doc: Long,
        page: Int,
        width: Int,
        height: Int,
        buffer: ByteBuffer
    ): Int  // return error code
    
    // Text extraction
    fun extractText(doc: Long, page: Int): String
    fun getTextBoundingBoxes(doc: Long, page: Int): List<TextBox>
    
    // Annotation support
    fun getAnnotations(doc: Long, page: Int): List<AnnotationData>
}

data class PageDimensions(val width: Float, val height: Float)
data class TextBox(val rect: Rect, val text: String)
```

### C++ Layer Implementation
- Maintain document pointer map
- Avoid repeated parsing
- Thread-safe access with mutex guards
- Error handling for all JNI calls

---

## 10. Performance Targets (Quantified)

| Metric                  | Target        | Rationale                              |
|-------------------------|---------------|----------------------------------------|
| First page render       | < 300 ms      | User perception: "instant"             |
| Page switch             | < 100 ms      | Smooth navigation feel                 |
| Zoom response           | < 16 ms/frame | 60 FPS smooth animation                |
| Stylus latency          | < 10-15 ms    | Responsive, natural writing            |
| OCR (per page)          | < 2-4 sec     | Reasonable for background processing   |
| App cold start          | < 2 sec       | Recent file opening                    |
| Search response (1000pp)| < 500 ms      | FTS5 indexed search                    |
| Memory (idle)           | < 100 MB      | Base app footprint                     |
| Memory (rendering)      | < 256 MB      | With tile cache                        |

---

## 11. CI/CD Pipeline

### GitHub Actions Configuration

#### Pipeline Stages
1. **Build native libs (NDK)**
   - Compile MuPDF and Tesseract
   - Generate JNI bindings
2. **Build APK**
   - Debug and release variants
3. **Run tests**
   - Unit tests (JNI, annotation math, OCR parsing)
   - Lint checks (ktlint, Android Lint)
4. **Output artifacts**
   - Debug APK
   - Release APK (optionally signed)
   - Test reports

#### Triggers
- Every push to main and feature branches
- Manual workflow dispatch for releases

---

## 12. Testing Strategy

### Unit Tests
- **JNI bindings:** Verify C++ <-> Kotlin communication
- **Annotation math:** Path interpolation, bounding box calculations
- **OCR parsing:** hOCR layout reconstruction

### Instrumented Tests
- **Rendering correctness:** Visual regression tests on known PDFs
- **Gesture handling:** Pinch, swipe, long-press
- **Stylus input:** Pressure and tilt event processing
- **Text selection:** Boundary detection accuracy

### Performance Tests
- **Large PDF stress test:** 500MB, 1000+ page PDFs
- **Memory leaks:** Native code profiling (AddressSanitizer)
- **Rendering performance:** Frame time analysis

### Manual Testing Checklist
- [ ] Test on phone (6" screen, various Android versions)
- [ ] Test on tablet (10" screen, multi-window mode)
- [ ] Test with S Pen on Samsung device
- [ ] Test with generic stylus on other tablets
- [ ] Stress test with fragmented, corrupted PDFs

---

## 13. First Executable Milestone (Week 1–2)

### Deliverable: Minimal PDF Viewer

#### Must Include
1. ✅ Load PDF from storage (file picker)
2. ✅ Render first page to SurfaceView
3. ✅ Swipe left/right navigation
4. ✅ Page indicator (current/total)

#### Explicit Out-of-Scope
- NO annotations
- NO OCR
- NO AI
- NO cloud sync
- NO form filling

#### Success Criteria
- App builds and runs on API 26+
- Opens a standard PDF (100 pages, < 50MB)
- Renders first page in < 500ms
- Swiping navigates between pages without crashes

---

## 14. Final Execution Order (Strict Sequence)

1. **Setup:** Project structure, CI/CD, repository
2. **MuPDF build + JNI bridge:** Get native rendering working
3. **Render single page:** Display PDF page in SurfaceView
4. **Add navigation:** Swipe, page controls, zoom
5. **Add caching:** Tile-based LRU cache for performance
6. **Add text extraction:** Character selection, copy to clipboard
7. **Add basic annotations:** Highlight, underline, sticky notes
8. **Add stylus support:** Low-latency drawing, pressure sensitivity
9. **Add tablet UI:** Dual-pane layout, keyboard shortcuts
10. **Add page management:** Reorder, delete, rotate
11. **Add form filling:** AcroForms support
12. **Add OCR:** Tesseract integration, background processing
13. **Add search:** FTS5 indexing, highlight results
14. **Add AI/Cloud:** Optional features (Phase D)

---

## 15. Milestone Tracking

### Phase A Milestones
- [x] **MA.1** - Basic PDF loading and single page render
- [x] **MA.2** - Page navigation (swipe, page buttons)
- [x] **MA.3** - Zoom and scroll functionality
- [x] **MA.4** - Text selection and copy to clipboard
- [ ] **MA.5** - Basic highlight annotation
- [ ] **MA.6** - Local file library with Room database
- [ ] **MA.7** - Night mode toggle
- [ ] **MA.8** - Tablet-responsive UI layout
- [ ] **MA.9** - Performance optimization and testing
- [ ] **MA.10** - Beta 1 release on F-Droid

### Phase B Milestones
- [ ] **MB.1** - Stylus detection and palm rejection
- [ ] **MB.2** - Low-latency freehand drawing
- [ ] **MB.3** - Pressure sensitivity support
- [ ] **MB.4** - Shape tools (rectangles, circles, arrows)
- [ ] **MB.5** - Eraser tool
- [ ] **MB.6** - Dual-pane tablet layout
- [ ] **MB.7** - Page reordering (drag-and-drop)
- [ ] **MB.8** - Page insertion and deletion
- [ ] **MB.9** - Basic form filling (text fields, checkboxes)
- [ ] **MB.10** - Signature capture and placement
- [ ] **MB.11** - Beta 2 release

### Phase C Milestones
- [ ] **MC.1** - Tesseract integration and build
- [ ] **MC.2** - Background OCR with WorkManager
- [ ] **MC.3** - hOCR parsing and layout reconstruction
- [ ] **MC.4** - Full-text search indexing (FTS5)
- [ ] **MC.5** - Search UI with snippet preview
- [ ] **MC.6** - OCR text export (plain text)
- [ ] **MC.7** - OCR text export (searchable PDF)
- [ ] **MC.8** - Performance optimization for large PDFs
- [ ] **MC.9** - Beta 3 release

### Phase D Milestones (Optional)
- [ ] **MD.1** - llama.cpp integration for on-device AI
- [ ] **MD.2** - WebDAV cloud sync setup
- [ ] **MD.3** - Google Drive / Dropbox integration
- [ ] **MD.4** - User API key management for cloud AI
- [ ] **MD.5** - PDF conversion utilities
- [ ] **MD.6** - Final release v1.0

---

## Decision Log

### Decision 1: TextureView vs Second SurfaceView
**Date:** March 21, 2026  
**Decision:** Use TextureView for annotations overlay  
**Rationale:** Better alpha blending, easier synchronization with SurfaceView  
**Outcome:** Cleaner rendering architecture

### Decision 2: Annotation Storage Model
**Date:** March 21, 2026  
**Decision:** Store annotations separately from PDF initially  
**Rationale:** Faster iteration, safer user experience, can flatten to PDF later  
**Outcome:** Enables rapid feature development

### Decision 3: OCR Storage
**Date:** March 21, 2026  
**Decision:** Use SQLite FTS5 for searchable OCR index (never modify original PDF)  
**Rationale:** No corruption risk, fast search, easy iteration  
**Outcome:** Reliable full-text search capability

---

## Project Health Indicators

### Success Metrics
- [ ] **Code quality:** > 80% test coverage for critical paths
- [ ] **Performance:** All targets met (see section 9)
- [ ] **Community:** > 50 GitHub stars by Phase B completion
- [ ] **Stability:** < 1% crash rate in beta releases
- [ ] **Compliance:** Full AGPL compliance verified at release

### Red Flags to Monitor
- JNI memory leaks detected in AddressSanitizer
- Stylus latency consistently > 20ms
- OCR accuracy < 90% on standard documents
- Tile rendering causing visual artifacts
- Community bug reports > 10/week without resolution

---

## Contact & Governance

**Project Lead:** [Your Name]  
**Repository:** [GitHub URL]  
**License:** AGPL v3  
**Community:** Discussions enabled on GitHub  
**Contributing:** See CONTRIBUTING.md for guidelines

---

## Revision History

| Date       | Version | Changes                              | Author |
|------------|---------|--------------------------------------|--------|
| 2026-03-21 | 1.0     | Initial comprehensive roadmap       | You    |

---

**Next Action:** Begin Phase A Week 1-2 execution with MuPDF JNI bridge setup.
