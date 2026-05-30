# AI-Powered IDE Setup — ValueLens

This document describes how to configure a development environment that uses AI tooling effectively throughout the ValueLens build process.

---

## Recommended IDE

**Android Studio Hedgehog (2023.1.1) or later**

Android Studio is required for:
- Accessibility Inspector (Layout Inspector → Accessibility mode)
- Android Emulator with accessibility simulation
- APK Analyzer for inspecting ML Kit and TFLite binary size
- Profiler for CPU, memory, and network during overlay operation

**Do not use VS Code as the primary Android IDE.** Kotlin language server support in VS Code is functional but inferior for Android-specific APIs. Use VS Code only for backend Go development.

---

## AI Coding Tools

### GitHub Copilot (or Cursor)

Configure for the following tasks:

**Accessibility node traversal boilerplate:**
Accessibility API code is verbose. Use Copilot to generate:
- `AccessibilityNodeInfo` recursive traversal functions
- Node pattern matchers for product card detection
- performAction wrappers for sort/filter interactions

**Prompt pattern for Copilot:**
```
// Traverse accessibility tree from root, collect all nodes matching:
// - has a child text node matching price pattern (₹[0-9]+)
// - has a sibling or child text node matching weight pattern ([0-9]+g|[0-9]+ml)
// Return list of ProductNode with name, price, weight raw strings
```

**Regex + unit parser generation:**
The unit normalizer has many edge cases. Use AI to generate comprehensive test cases:
```
// Generate 30 diverse test cases for parseWeightString():
// inputs: "200g", "1kg", "1.5 L", "500ml", "1000 grams", "2 x 100g", "pack of 3 (150g each)"
// expected output: NormalizedUnit(value: Double, unit: BaseUnit)
```

**TFLite intent classifier:**
Use Copilot to scaffold the TensorFlow Lite model loading and inference pipeline for the on-device intent classifier. The model loading boilerplate is repetitive.

---

### Claude (via API or claude.ai)

Use Claude for architecture and design decisions, not line-by-line code:

**Effective Claude use cases for this project:**

1. **Designing the CapabilityMap schema** — describe what you've seen in Zepto's UI and ask Claude to suggest the right data structure
2. **Prompt engineering for AI reasoning** — iterate on the product comparison prompt structure until Claude returns consistent JSON
3. **Edge case analysis** — "What product card formats might break this regex? Give 20 examples"
4. **Security review** — paste the Accessibility Service code and ask for a threat analysis
5. **Play Store policy interpretation** — describe the feature and ask for policy compliance analysis

---

## Project Structure

```
valuelens-android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/in/valuelens/
│   │   │   ├── accessibility/
│   │   │   │   ├── ValueLensAccessibilityService.kt
│   │   │   │   ├── NodeTraversal.kt
│   │   │   │   ├── CapabilityMapper.kt
│   │   │   │   └── ActionExecutor.kt
│   │   │   ├── ocr/
│   │   │   │   ├── ScreenCapture.kt
│   │   │   │   └── MLKitOCRProcessor.kt
│   │   │   ├── parser/
│   │   │   │   ├── ProductParser.kt
│   │   │   │   ├── UnitNormalizer.kt
│   │   │   │   └── ProductCard.kt
│   │   │   ├── engine/
│   │   │   │   ├── ValueEngine.kt
│   │   │   │   ├── DiscountVerifier.kt
│   │   │   │   └── QueryPlanner.kt
│   │   │   ├── ai/
│   │   │   │   ├── IntentClassifier.kt      (TFLite)
│   │   │   │   └── CloudReasoningClient.kt  (backend proxy)
│   │   │   ├── overlay/
│   │   │   │   ├── OverlayManager.kt
│   │   │   │   ├── FloatingButton.kt
│   │   │   │   └── ResultPanel.kt           (Compose)
│   │   │   ├── storage/
│   │   │   │   ├── AppDatabase.kt           (Room + SQLCipher)
│   │   │   │   ├── PreferenceDao.kt
│   │   │   │   └── QueryHistoryDao.kt
│   │   │   └── ui/
│   │   │       ├── onboarding/
│   │   │       └── settings/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── src/test/ + src/androidTest/
├── models/
│   └── intent_classifier.tflite
└── build.gradle.kts

valuelens-backend/
├── cmd/api/main.go
├── internal/
│   ├── handler/
│   │   ├── reason.go
│   │   └── auth.go
│   ├── claude/
│   │   └── client.go
│   ├── cache/
│   │   └── redis.go
│   └── middleware/
│       ├── auth.go
│       └── ratelimit.go
├── go.mod
├── Dockerfile
└── .github/workflows/
```

---

## Development Workflow

### Testing Accessibility Code

The emulator does not replicate real app accessibility trees accurately for Zepto or Blinkit. Use a physical Android device for accessibility development.

**Layout Inspector — Accessibility view:**

In Android Studio, with the app running on device:
`View → Tool Windows → Layout Inspector`

Then enable "Show Accessibility labels" to see what your AccessibilityService will actually receive.

**Accessibility Scanner (Google app):**

Install Google's Accessibility Scanner on the test device and use it to audit what the shopping app exposes in its accessibility tree before writing your node matchers.

---

### Testing OCR Fallback

Capture screenshots from target apps manually using `adb screencap` and run the OCR pipeline against them as unit tests. This avoids needing the device to be live during every test run.

```bash
adb shell screencap -p /sdcard/zepto_screen.png
adb pull /sdcard/zepto_screen.png src/test/resources/
```

Then in the test:
```kotlin
val bitmap = BitmapFactory.decodeFile("src/test/resources/zepto_screen.png")
val result = MLKitOCRProcessor.process(bitmap)
assertEquals("Colgate Strong Teeth", result.products[0].name)
```

---

### Latency Profiling

Use Android Studio's CPU Profiler with the "Sample Java/Kotlin methods" mode to identify where time is spent in the query pipeline. Target breakdown:

- Accessibility tree traversal: < 100ms
- Product parsing: < 80ms
- Value engine math: < 20ms
- Network call to backend: < 1200ms (P90)
- Compose recomposition for result render: < 50ms

If any step exceeds its budget, profile before optimizing — do not guess.

---

## Key Dependencies — build.gradle.kts

```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Accessibility + overlay are framework APIs, no dependency needed

    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Room + SQLCipher
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    ksp("androidx.room:room-compiler:2.6.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```
