# Lensly — Phase 1 Foundation: Implementation Log

## Status: PHASE 1 MVP COMPLETE ✅

---

## What Was Built — Step 1 (Today)

### Android Project (`/android`)
Scaffolded via `android create empty-activity` CLI with:
- `minSdk = 28` (Android 9)
- `targetSdk = 36`
- Jetpack Compose enabled

### Manifest Updates (`AndroidManifest.xml`)
Added permissions:
- `SYSTEM_ALERT_WINDOW` — overlay floating button
- `INTERNET` — Go backend API calls
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — persistent overlay
- `RECORD_AUDIO` — voice query input
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — prevent service kill

Registered components:
- `LenslyAccessibilityService` — screen reader
- `OverlayService` — floating button + panel manager

---

## Core Kotlin Files Created

| File | Purpose |
|------|---------|
| `models/Models.kt` | All data classes: Product, NormalizedUnit, RankedProduct, QueryIntent, UserPreferences |
| `parser/UnitNormalizer.kt` | Converts "1kg", "500ml", "Pack of 3" → base SI units |
| `parser/ProductParser.kt` | Extracts name, price, weight, MRP, discount from raw card text |
| `engine/ValueEngine.kt` | Deterministic math: ₹/gram ranking, fake discount detection, usage duration |
| `screen/NodeTraverser.kt` | Accessibility tree traversal with heuristic card detection |
| `service/LenslyAccessibilityService.kt` | Listens to content changes in Zepto/Blinkit/Amazon |
| `overlay/OverlayService.kt` | Foreground service managing floating button lifecycle |
| `overlay/FloatingButtonView.kt` | Draggable Compose bubble attached to WindowManager |
| `screen/OcrParser.kt` | On-device ML Kit OCR scanner fallback |

---

## Go Backend (`/backend`)

| File | Purpose |
|------|---------|
| `main.go` | Chi router, CORS middleware, zero-dependency `.env` loader |
| `handlers/health.go` | GET /health — readiness check |
| `handlers/analyze.go` | POST /api/v1/analyze — routes to AI or deterministic ranking |
| `models/product.go` | Shared data models (Product, RankedProduct, QueryIntent, AnalyzeResponse) |
| `claude/client.go` | Claude API integration with structured prompts (resolved imports cycle) |
| `.env.example` | Environment variable template |

---

## Architecture Decisions Made

1. **Go API gateway from Day 1** — Claude API key stays server-side, never in APK
2. **Heuristic node detection** — detect cards by (price + weight in subtree) pattern, not app-specific selectors
3. **Deterministic-first routing** — `requiresAIReasoning()` gates AI calls to complex objectives only
4. **Local broadcast IPC** — AccessibilityService → OverlayService via local Intent broadcast
5. **Lifecycle-aware Services** — Manual implementation of Lifecycle/VM/SavedState interfaces in background OverlayService to host Jetpack Compose windows safely

---

## Phase 1 Implementation Checklist

- [x] Add Retrofit + OkHttp dependencies to `build.gradle.kts`
- [x] Build `ApiClient.kt` — typed Retrofit interface to Go backend
- [x] Build `OverlayPanel.kt` — Compose side panel with result cards
- [x] Run `go mod tidy` and verify backend compiles
- [x] Write unit tests for `UnitNormalizer` and `ValueEngine`
- [x] Implement ML Kit OCR screen scanning fallback
- [x] Add dynamic search, chip actions, and voice SpeechRecognizer to overlay
- [x] Fix compile and classpath dependencies on Kotlin 2.2.10 / AGP 9.0.1

---

## Env Setup Required

Create a `.env` file in the `/backend` directory containing:
```
ANTHROPIC_API_KEY=your_actual_key_here
PORT=8080
```

Get your API key from: https://console.anthropic.com

