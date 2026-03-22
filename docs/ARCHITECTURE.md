# Architecture Decision Records (ADRs)

## Overview
This directory contains Architecture Decision Records (ADRs) for significant design choices in the PDF Reader project. Each ADR documents the decision, rationale, and implications.

---

## ADR-001: MuPDF as Primary Rendering Engine

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
We needed to select a PDF rendering engine that:
- Supports AGPL licensing
- Offers high performance on Android
- Handles complex PDFs reliably
- Has active maintenance and community support

### Decision
We selected **MuPDF** as the primary rendering engine, accessed via JNI from Kotlin code.

### Rationale
- **AGPL compatible:** MuPDF is available under AGPL, allowing us to satisfy open-source licensing requirements
- **Performance:** MuPDF is lightweight and optimized for mobile devices
- **Reliability:** Mature codebase with excellent PDF compliance
- **C API:** Clean C API makes JNI integration straightforward
- **No licensing fees:** Open-source eliminates commercial dependencies

### Implications
- ✅ Must maintain C++ JNI bridge code
- ✅ Need to handle native memory management carefully
- ✅ Performance gains from native rendering outweigh JNI overhead
- ✅ Community can contribute to open-source engine improvements

### Alternatives Considered
1. **PDFium (Chromium):** LGPL/open-source but heavier, more complex C API
2. **Apache PDFBox:** Pure Java, slower on Android, heavier memory footprint
3. **Commercial engines:** Ruled out due to AGPL licensing requirement

---

## ADR-002: TextureView for Annotation Overlay

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
Annotation rendering (stylus drawing, shapes, highlights) requires:
- Low-latency drawing response
- Transparency and alpha blending
- Smooth compositing with PDF underneath
- Real-time pressure/tilt support

### Decision
Use **TextureView** for the annotation overlay, with **SurfaceView** for the PDF underneath.

### Rationale
- **Synchronization:** TextureView renders on the UI thread, avoiding SurfaceView Z-ordering issues
- **Blending:** Supports transparent drawing over the PDF naturally
- **Performance:** GPU-accelerated rendering path available
- **Flexibility:** Easier to add effects and filters later

### Implications
- ✅ Annotation rendering is on UI thread (must stay responsive)
- ✅ GPU path optional but highly recommended for pressure sensitivity
- ✅ Simpler architecture than dual SurfaceView setup
- ✅ Compatible with Canvas and OpenGL backends

### Alternatives Considered
1. **Dual SurfaceView:** Z-ordering complications, synchronization issues
2. **Canvas overlay:** Slower but simpler (fallback option)
3. **GLSurfaceView:** More complex setup, not necessary for Phase A

---

## ADR-003: Separate Annotation Storage Model

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
We must decide whether to:
1. Store annotations in the original PDF file
2. Store annotations separately from the PDF

### Decision
**Store annotations separately from the PDF** in Phase A, with future capability to flatten into PDF.

### Rationale
- **Safety:** No risk of corrupting original PDF during development
- **Performance:** Faster iteration—no re-serialization of PDF
- **Undo/Redo:** Easier to implement with separate history
- **User experience:** Users can clear annotations without losing original
- **Future flexibility:** Can export to PDF with flattened annotations later

### Implications
- ✅ Room database stores annotations separately
- ✅ Export function required to flatten annotations into PDF
- ✅ Requires robust association between annotations and document
- ✅ Search must consider both PDF and annotation layers

### Alternatives Considered
1. **PDF-embedded annotations:** Higher risk, requires PDF rewriting
2. **Cloud-only storage:** Not offline-compatible, increases complexity

---

## ADR-004: SQLite FTS5 for Full-Text Search

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
Full-text search (Phase C) must support:
- Fast queries on large documents (1000+ pages)
- OCR'd text and native PDF text
- Snippet previews
- No external dependencies

### Decision
Use **SQLite FTS5** (Full-Text Search extension) within Room database.

### Rationale
- **Performance:** < 500ms search on 1000-page document
- **No external dep:** SQLite built into Android
- **Simplicity:** SQL-based queries, familiar syntax
- **Snippets:** Built-in highlighting and context extraction
- **Ranking:** Automatic relevance ranking available

### Implications
- ✅ Room FTS5 integration required (add FTS5 dependency)
- ✅ OCR indexing must happen in background (WorkManager)
- ✅ Search index grows with document size (manageable for typical docs)
- ✅ Supports boolean operators and phrase queries

### Alternatives Considered
1. **Elasticsearch:** Over-engineered, external service required
2. **Manual indexing:** Performance limitations, reinventing the wheel
3. **Regex search:** Too slow for large documents

---

## ADR-005: AGPL v3 License

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
License selection must:
- Be compatible with MuPDF (AGPL)
- Encourage community contributions
- Protect the project from proprietary forks
- Allow commercial use with conditions

### Decision
Use **AGPL v3** as the project license.

### Rationale
- **MuPDF compatibility:** Direct requirement, no workaround needed
- **Copyleft:** Ensures improvements flow back to community
- **Freedom:** Users have rights to study, modify, share code
- **Dual licensing:** Can offer commercial licenses separately if desired
- **Clarity:** Clear legal framework for contributors

### Implications
- ✅ Derivative works must also use AGPL
- ✅ Network use triggers copyleft (if distributed as service)
- ✅ Commercial deployment allowed with source disclosure
- ✅ Contributors must sign CLA (optional, recommended)
- ✅ Distribution on Google Play requires source transparency

### Alternatives Considered
1. **Apache 2.0:** Permissive but incompatible with AGPL MuPDF
2. **GPL v3:** Network clause in AGPL better for web scenarios
3. **MIT:** Too permissive, wouldn't protect community improvements

---

## ADR-006: Jetpack Compose for UI

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
UI framework must support:
- Responsive layouts (phone + tablet)
- Modern Material Design
- Declarative programming model
- Active Google support

### Decision
Use **Jetpack Compose** for non-rendering UI (menus, dialogs, library, settings).

### Rationale
- **Modern:** Declarative, reactive model reduces bugs
- **Responsive:** Built-in support for tablet layouts
- **Material 3:** Latest design guidelines out of box
- **Performance:** Optimized composition tree
- **Official support:** First-class Android framework

### Implications
- ✅ Compose for UI framework, custom views for rendering
- ✅ Requires API 21+ (widely compatible)
- ✅ Kotlin required (already chosen)
- ✅ Less control over every pixel vs XML (acceptable for library UI)

### Alternatives Considered
1. **XML + traditional Views:** Legacy, less expressive, more boilerplate
2. **Flutter/React Native:** Overkill, not native platform
3. **Custom Canvas:** Possible but reinvents too much wheel

---

## ADR-007: Tesseract for OCR (Phase C)

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
OCR engine must:
- Work offline (no cloud dependency)
- Support multiple languages
- Have reasonable accuracy (> 90%)
- Be lightweight (< 100MB)

### Decision
Use **Tesseract** (open-source OCR engine) via native library.

### Rationale
- **Offline:** No cloud API dependency, privacy-first
- **Languages:** Supports 100+ languages out of box
- **Accuracy:** Industry-standard, 90%+ on printed text
- **Size:** Traineddata files ~5-10MB per language
- **Maturity:** 30+ years of development

### Implications
- ✅ Background processing required (WorkManager)
- ✅ hOCR parsing for layout reconstruction
- ✅ User storage needed for traineddata (~50MB for 5 languages)
- ✅ Processing time ~2-4 sec per page (acceptable for background)

### Alternatives Considered
1. **Cloud OCR (Google, Azure):** Privacy concerns, connectivity required
2. **Commercial OCR (ABBYY):** Licensing costs, overkill
3. **ML Kit:** Limited language support, Google-only

---

## ADR-008: Room + Kotlin Coroutines for Data Layer

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
Data persistence must support:
- Annotations, documents, OCR index, search
- Asynchronous operations
- Type safety
- Easy testing

### Decision
Use **Room** (SQLite wrapper) with **Kotlin Coroutines** for reactive data access.

### Rationale
- **Type-safe:** Compile-time SQL validation
- **Async:** Coroutines prevent UI blocking
- **Testing:** Easy mocking and in-memory databases
- **Jetpack:** Official Google library, well-maintained
- **Reactive:** LiveData/Flow integration for observability

### Implications
- ✅ DAOs (Data Access Objects) for each entity
- ✅ Migrations required as schema evolves
- ✅ Must test database layer in instrumented tests
- ✅ Background work via WorkManager for OCR/sync

### Alternatives Considered
1. **Realm:** Powerful but heavier, overkill for this use case
2. **Raw SQLite:** No type safety, more boilerplate
3. **Firestore:** Cloud-only, doesn't fit offline-first design

---

## ADR-009: Katmull-Rom Splines for Stylus Interpolation

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
Stylus input produces discrete points. To create smooth curves:
- Need smooth interpolation between points
- Must respond in real-time (< 16ms per frame)
- Handle variable point density

### Decision
Use **Catmull-Rom splines** for interpolating stylus stroke points.

### Rationale
- **Smoothness:** Creates natural-looking curves from discrete input
- **Performance:** O(1) per segment, fast computation
- **Control:** Passes through actual sampled points (no deviation)
- **Locality:** Each segment uses 4 control points (cache-friendly)
- **Proven:** Industry standard for digital pen input

### Implications
- ✅ Implement spline math in Kotlin or C++ (performance-critical)
- ✅ Cache interpolated points for rendering
- ✅ Test with various stylus input rates (60-240 Hz)
- ✅ GPU path can use geometry shaders for batching

### Alternatives Considered
1. **Linear interpolation:** Too angular, unnatural
2. **Bézier curves:** More complex, overkill
3. **Direct point rendering:** Jerky, poor UX

---

## ADR-010: WorkManager for Background Processing

**Date:** March 21, 2026  
**Status:** Accepted  
**Deciders:** Senior Engineering Team

### Context
Background tasks (OCR, indexing, cloud sync) must:
- Survive app termination
- Respect device battery/connectivity constraints
- Queue work reliably
- Integrate with JobScheduler

### Decision
Use **WorkManager** for all background processing.

### Rationale
- **Reliability:** Persistent queue, survives restarts
- **Constraints:** Respect battery, connectivity, device idle
- **Unified API:** Abstracts JobScheduler, GCM, Alarm Manager
- **Debugging:** Built-in logging and inspection
- **Official:** Google-recommended for Android 5.0+

### Implications
- ✅ Define Workers for OCR, indexing, sync
- ✅ Chain work for dependent tasks (e.g., OCR then index)
- ✅ Handle failures with retry policies
- ✅ Test with WorkManager testing utilities

### Alternatives Considered
1. **JobScheduler:** Lower-level, more code
2. **Firebase Cloud Functions:** Requires backend, not suitable for local work
3. **Simple threading:** No reliability, kills battery

---

## Decision Tracking Template

For future ADRs, use this template:

```markdown
## ADR-NNN: [Title]

**Date:** [Date]  
**Status:** [Proposed | Accepted | Deprecated | Superseded by ADR-XXX]  
**Deciders:** [Names]

### Context
[What problem are we trying to solve?]

### Decision
[What did we decide?]

### Rationale
[Why did we choose this?]

### Implications
[What are the consequences?]

### Alternatives Considered
[Other options evaluated and why they were rejected]
```

---

**Last Updated:** March 21, 2026
