# Tech Stack — ValueLens

Every choice below is made with a reason. No cargo-culting.

---

## Mobile App — Android

**Language: Kotlin**

Kotlin is the official Android language, has first-class Jetpack support, and has better null safety than Java. Coroutines make async accessibility + network calls clean without callback hell.

**Minimum SDK: API 28 (Android 9)**

Android 9 covers 96%+ of active Android devices in India as of 2025. MediaProjection API (for screenshot capture) and Accessibility Service improvements stabilized at API 28. Going lower introduces significant compatibility burden with no meaningful user gain.

**Target SDK: API 34 (Android 14)**

Required for Play Store compliance and necessary to use the latest WindowManager overlay behavior (TYPE_APPLICATION_OVERLAY).

---

## UI Framework

**Jetpack Compose**

Overlay panels and result cards are dynamic and state-driven. Compose eliminates the boilerplate of XML + ViewBinding for complex UI, handles state reactivity cleanly, and makes animation straightforward. The overlay panel is a ComposeView hosted in a WindowManager-attached View.

**Why not Flutter or React Native?**

ValueLens is deeply Android-native: AccessibilityService, MediaProjection, WindowManager overlays, and system-level permissions are all Android APIs. Flutter and React Native add abstraction layers that complicate or block access to these APIs. Native Kotlin is the only sensible choice.

---

## Screen Understanding

**Android Accessibility API (AccessibilityService)**

The primary screen reading mechanism. AccessibilityService gives structured access to the UI tree of any foreground app — text content, element bounds, interactive controls — without requiring a screenshot. This is faster and more accurate than OCR for apps that expose their accessibility tree properly (Zepto, Blinkit, Amazon all do).

Use `AccessibilityNodeInfo` traversal to extract product cards by detecting repeating list patterns.

**ML Kit On-Device OCR (fallback)**

When the Accessibility tree is insufficient (some screens in JioMart, image-heavy layouts), use Google ML Kit's Text Recognition v2. It runs entirely on-device, has no per-call cost, and achieves high accuracy on printed product text.

**Why not a remote vision model for OCR?**

Remote vision adds 800ms–2s of latency just for text extraction. On-device OCR handles 90%+ of cases and keeps latency well inside the 2-second target.

**MediaProjection API**

Used to capture a screenshot of the foreground app when needed for OCR fallback. Requires a one-time user permission prompt (Android 14: per-session permission).

---

## On-Device AI / ML

**TensorFlow Lite — Intent Classifier**

A lightweight intent classification model (< 5MB) that maps user queries to structured intents: `{objective: "minimize_price_per_gram", category: "toothpaste"}`. This runs on-device in < 50ms and avoids sending the raw query text to the cloud for simple cases.

Trained on a labeled query dataset. Updated via model download, not app update.

**Why not send all queries to the cloud?**

Latency and cost. Simple queries like "best value" do not need a large language model. The intent classifier handles them deterministically. Cloud AI is reserved for queries that genuinely need reasoning.

---

## Cloud AI Reasoning

**Anthropic Claude API (claude-sonnet-4)**

Used for nuanced multi-factor queries: ingredient analysis, "healthiest", "worth the extra ₹50", subjective comparisons. The API receives a structured JSON payload (never raw screen data) and returns a ranked explanation.

**Why Claude over GPT-4 or Gemini?**

Claude's instruction-following on structured JSON input is reliable and produces consistent output format. For a production agent that needs to parse model output programmatically, consistency matters more than benchmark scores. Claude also has strong reasoning on unit-based comparisons.

**Prompt structure:**

```json
{
  "products": [...parsed product array...],
  "user_query": "healthiest toothpaste that isn't too expensive",
  "user_preferences": {...on-device profile...},
  "task": "rank and explain"
}
```

The model never receives raw screen content. It only receives structured data produced by the on-device parser.

---

## Backend

**Language: Go**

The backend is a thin API gateway and caching layer. Go is chosen for:
- Very low memory footprint (important when running many concurrent overlay sessions)
- Fast cold starts on serverless platforms
- Excellent standard library for HTTP servers
- Simple deployment as a single binary

**Why not Node.js or Python?**

Python has slower startup and higher memory per request. Node.js works but Go outperforms it for pure HTTP gateway workloads with no meaningful developer experience cost.

**Framework: Chi (Go HTTP router)**

Minimal, idiomatic, no magic. Chi handles routing, middleware, and request context cleanly.

---

## Backend Infrastructure

**Platform: Railway or Fly.io**

Both support Go containers, scale to zero, and have data centers in Singapore (low latency for India). Railway is simpler for V1; Fly.io gives more control over regions.

**Why not AWS/GCP?**

Operational overhead at V1 is not justified. Railway/Fly.io provide 90% of what AWS gives at 10% of the setup cost. Migrate to AWS when traffic demands it.

**Database: PostgreSQL (for V2+)**

V1 stores nothing server-side. V2 introduces anonymized query analytics and product price baseline database. PostgreSQL is the default relational choice — mature, well-supported, good full-text search.

**Cache: Redis**

Used for:
- Caching AI responses for identical product-set + query combinations
- Rate limiting per user
- Short-lived session state during multi-step agent queries (V3)

---

## Local Storage (On-Device)

**Room (Android SQLite ORM)**

Stores: user preferences, recent query history, cached product prices for cross-app comparison (V2), ingredient database subset.

**Why not shared preferences or a file?**

Room supports typed queries, migrations, and encryption integration. Preferences are too flat for structured product/preference data.

**SQLCipher integration for encryption**

All locally stored data is encrypted at rest using SQLCipher, which integrates cleanly with Room.

---

## Networking

**Retrofit + OkHttp**

Standard Android HTTP stack. OkHttp handles connection pooling, timeout management, and interceptors (for auth token injection and logging). Retrofit provides type-safe API interface declarations.

**Why not Ktor?**

Retrofit + OkHttp has a larger community and more examples for Android-specific use cases. Ktor is a viable alternative but offers no material advantage here.

---

## Analytics (V2+)

**PostHog (self-hosted or cloud)**

Product analytics without selling user data. PostHog is open-source, GDPR-compliant, and captures funnel events, feature usage, and retention. Self-hosting on Fly.io is feasible.

**No analytics in V1.** Keeps the data model simple and maximizes user trust during launch.

---

## CI/CD

**GitHub Actions**

- On PR: lint (ktlint), unit tests, build APK
- On merge to main: build release APK, run instrumented tests on Firebase Test Lab
- On tag: upload to Play Store internal track via Fastlane

**Fastlane**

Handles Play Store upload, changelog generation, and screenshot upload.

---

## Summary Table

| Layer | Technology | Why |
|-------|-----------|-----|
| App language | Kotlin | Official Android, coroutines, null safety |
| UI | Jetpack Compose | Dynamic state-driven overlay UI |
| Screen reading | Accessibility API + ML Kit OCR | Structured first, image fallback |
| Screenshot | MediaProjection API | Required for OCR fallback |
| On-device ML | TensorFlow Lite | Fast intent classification, no latency |
| Cloud AI | Claude API (Sonnet) | Reliable structured reasoning |
| Backend | Go + Chi | Low memory, fast, simple deployment |
| Hosting | Railway / Fly.io | Low ops overhead, India-latency-aware |
| Local DB | Room + SQLCipher | Typed queries, encrypted at rest |
| Networking | Retrofit + OkHttp | Standard, well-supported |
| Analytics | PostHog (V2) | Open-source, privacy-respecting |
| CI/CD | GitHub Actions + Fastlane | Automated build and Play Store delivery |
