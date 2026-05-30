# Feature Log: OCR Fallback, Search Panel & Go Integration

This document logs the implementation of the advanced features for Lensly, including on-device OCR fallbacks, an interactive search overlay panel, and seamless Go backend integration.

---

## 🛠️ Features Implemented

### 1. Zero-Dependency Go Backend `.env` Loader
- Added a lightweight, standard-library-based `.env` parser inside the Go backend `main.go`.
- Automatically loads environment configurations (e.g. `ANTHROPIC_API_KEY`, `PORT`) from a local `.env` file on startup.

### 2. Circular Dependency Resolution (Go)
- Resolved the Go compiler `import cycle not allowed` bug between the `claude` and `handlers` packages.
- Extracted the `AnalyzeResponse` struct to the shared `models` package, allowing clean package separation.

### 3. Jetpack Compose Dynamic Lifecycle in Service
- Made `OverlayService` manually implement `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`.
- Enables running Jetpack Compose and architecture components (ViewModels, flows, coroutines) directly inside a persistent background service window (`TYPE_APPLICATION_OVERLAY`) without crashing.

### 4. ML Kit OCR Fallback (`OcrParser.kt`)
- Integrated Google ML Kit Text Recognition to scan screen bitmaps.
- Developed layout heuristics to automatically group parsed text lines into distinct product cards.
- Automatically triggers when the native accessibility node tree returns empty content.

### 5. Native Screen Capture (Android 11+)
- Exposed native accessibility screenshot capture in `LenslyAccessibilityService.kt`.
- Shares a thread-safe singleton instance of the accessibility service to allow the overlay context to trigger screen grabs.

### 6. Interactive Search Panel Composable (`OverlayPanel.kt`)
- Added a glassmorphic bottom search field to query or filter analyzed products.
- Included suggestion chips (`Best Value`, `Healthy`, etc.) to trigger quick-actions.
- Integrated voice recognition triggering native Android `SpeechRecognizer` speech-to-text.

---

## 📂 New & Modified Files

### 🖥️ Go Backend
| File | Action | Purpose |
|------|--------|---------|
| [main.go](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/backend/main.go) | `MODIFY` | Added zero-dependency `.env` parsing |
| [claude/client.go](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/backend/claude/client.go) | `MODIFY` | Removed handlers dependency, updated signature to `models.AnalyzeResponse` |
| [handlers/analyze.go](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/backend/handlers/analyze.go) | `MODIFY` | Removed duplicate `AnalyzeResponse` struct, migrated to `models.AnalyzeResponse` |
| [models/product.go](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/backend/models/product.go) | `MODIFY` | Added shared `AnalyzeResponse` struct definition |

### 📱 Android Application
| File | Action | Purpose |
|------|--------|---------|
| [OcrParser.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/screen/OcrParser.kt) | `NEW` | ML Kit text recognition scanner & product card parser |
| [FloatingButtonView.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/overlay/FloatingButtonView.kt) | `MODIFY` | Configured view tree owners, added dynamic click callbacks to service |
| [OverlayService.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/overlay/OverlayService.kt) | `MODIFY` | Standardized lifecycle interface, implemented SpeechRecognizer and screenshot pipeline |
| [OverlayPanel.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/ui/overlay/OverlayPanel.kt) | `MODIFY` | Added query text fields, suggestion chips, and voice mic trigger |
| [OverlayViewModel.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/ui/overlay/OverlayViewModel.kt) | `MODIFY` | Exposed direct loading and error state triggers for OCR pipeline |
| [LenslyAccessibilityService.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/service/LenslyAccessibilityService.kt) | `MODIFY` | Exposed static instance, added screenshot callback, serialized Gson broadcasts |
| [libs.versions.toml](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/gradle/libs.versions.toml) | `MODIFY` | Added `material-icons-core` dependency, corrected Kotlin/KSP versions to 2.2.10 |
| [build.gradle.kts](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/build.gradle.kts) | `MODIFY` | Added `material-icons-core` library implementation |
| [gradle.properties](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/gradle.properties) | `MODIFY` | Enabled `android.disallowKotlinSourceSets=false` to compile generated KSP files |

---

## 🧪 Verification & Testing
- **Compilation:** Clean Kotlin compiler passes with no errors (`BUILD SUCCESSFUL` via Gradle).
- **Unit Tests:** All **16 unit tests** (covering `UnitNormalizer` and `ValueEngine`) compile and pass successfully.
- **Go Compilation:** Backend successfully compiles with zero import cycles.
