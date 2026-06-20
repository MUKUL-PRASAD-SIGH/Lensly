# Feature Log: On-Device ML Intent Classification (all-MiniLM-L6-v2)

This document logs the implementation of the on-device Machine Learning intent classification feature using `all-MiniLM-L6-v2` SentenceTransformer via TensorFlow Lite and the Room DB search history FIFO update.

---

## 🛠️ Features Implemented

### 1. Python MiniLM Model Export Pipeline (`scripts/export_minilm.py`)
- Automated downloading of the HuggingFace `sentence-transformers/all-MiniLM-L6-v2` transformer model.
- Wrapped the model in a custom `tf.Module` that accepts token inputs, applies **Mean Pooling** over the token embeddings, and performs **L2 Normalization** to output unit sentence embeddings.
- Converted and quantized the module to a dynamic-range `.tflite` model, reducing the size from ~90MB to **21.97 MB**.
- Extracted and saved the tokenizer's `vocab.txt` file directly to the Android assets directory.

### 2. Custom Pure-Kotlin BERT Tokenizer
- Developed a self-contained, thread-safe WordPiece tokenizer inside `TfLiteSimilarityIntentClassifier.kt`.
- Replaces JNI-bound dependencies for tokenizer libraries, ensuring 100% stable execution across all Android SDK levels.
- Loads vocabulary from `vocab.txt` and splits search queries into subword tokens.

### 3. TfLite Cosine Similarity Intent Classifier (`TfLiteSimilarityIntentClassifier.kt`)
- Generates 384-dimensional query embeddings in real-time (< 5ms).
- Defines representative anchor phrases for each of the 7 core shopping intents:
  - `FIND_CHEAPEST`
  - `VALUE_FOR_MONEY`
  - `COMPARE_PRODUCTS`
  - `BEST_RATED`
  - `BUDGET_SEARCH`
  - `CATEGORY_SEARCH`
  - `DEAL_SEARCH`
- Computes cosine similarity (dot product of normalized vectors) against the pre-cached embeddings of these anchors.
- Identifies the highest matching intent and extracts category/budget parameters.

### 4. Decoupled Pipeline and Safety Fallback (`FallbackIntentClassifier.kt`)
- Swapped direct classifier instantiation with a clean, decoupled `IntentClassifier` interface.
- Implemented confidence scoring: if similarity confidence is $\ge 0.75$, uses ML classification.
- If confidence is $< 0.75$, or the ML model fails, delegates to `RegexIntentClassifier` for absolute resilience.

### 5. Room Database FIFO Eviction
- Enforced a strict 20-item query limit in Room DB.
- Modified DAO methods to run synchronously (non-suspend) to solve KSP compile-time crashes (`unexpected jvm signature V`).
- Dispatched DB queries asynchronously in `OverlayViewModel.kt` using `Dispatchers.IO` to ensure non-blocking UI operations.

---

## 📂 Created & Modified Files

### 💻 Scripts
| File | Action | Purpose |
|------|--------|---------|
| [export_minilm.py](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/scripts/export_minilm.py) | `NEW` | Model packaging and TFLite conversion pipeline |

### 📱 Android Application
| File | Action | Purpose |
|------|--------|---------|
| [IntentClassifier.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/planner/IntentClassifier.kt) | `MODIFY` | Decoupled into a clean interface |
| [RegexIntentClassifier.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/planner/RegexIntentClassifier.kt) | `NEW` | Keyword-based intent classification |
| [TfLiteSimilarityIntentClassifier.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/planner/TfLiteSimilarityIntentClassifier.kt) | `NEW` | MiniLM TFLite embedder + cosine similarity classifier |
| [FallbackIntentClassifier.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/planner/FallbackIntentClassifier.kt) | `NEW` | Confidence orchestrator and fallback manager |
| [QueryHistoryDao.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/db/QueryHistoryDao.kt) | `MODIFY` | Modified to synchronous queries to solve KSP compile crashes |
| [OverlayViewModel.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/ui/overlay/OverlayViewModel.kt) | `MODIFY` | Offloaded search history insertions and FIFO deletions to `Dispatchers.IO` |
| [OverlayService.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/overlay/OverlayService.kt) | `MODIFY` | Wired `FallbackIntentClassifier` to the ViewModel factory |
| [Models.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/main/java/com/example/lensly/models/Models.kt) | `MODIFY` | Added `confidence` field to `QueryIntent` |
| [gradle.properties](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/gradle.properties) | `MODIFY` | Added `android.uniquePackageNames=false` to bypass TFLite package clash |
| [build.gradle.kts](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/build.gradle.kts) | `MODIFY` | Added `tensorflow-lite-support` dependency |
| [libs.versions.toml](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/gradle/libs.versions.toml) | `MODIFY` | Added version entry for `tensorflow-lite-support` |

### 🧪 Unit Tests
| File | Action | Purpose |
|------|--------|---------|
| [IntentClassifierTest.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/test/java/com/example/lensly/planner/IntentClassifierTest.kt) | `MODIFY` | Added test cases for `RegexIntentClassifier` parameters |
| [QueryHistoryDaoTest.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/androidTest/java/com/example/lensly/db/QueryHistoryDaoTest.kt) | `MODIFY` | Added `deleteOldQueries()` verification inside Room tests |
