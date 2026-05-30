# Architecture — ValueLens

---

## Design Principles

1. **Optimization-first, AI-last.** Use deterministic operations before invoking any model.
2. **Local-first.** All sensitive data lives on-device. Cloud receives only structured, anonymized payloads.
3. **Fail gracefully.** If any layer times out or fails, degrade to the next best output rather than showing an error.
4. **Separation of concerns.** Each layer has a single job. No layer does math and reasoning and UI.

---

## System Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    USER INTERACTION                          │
│         Floating Button → Overlay Panel → Query Input        │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│               SCREEN UNDERSTANDING LAYER                     │
│   AccessibilityService → Node Traversal → Product Extractor  │
│                    ↓ (fallback)                              │
│          MediaProjection → ML Kit OCR → Text Parser          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                ACTION DISCOVERY ENGINE                       │
│    Detect: sort controls, filters, search bar, pagination    │
│    Output: CapabilityMap { sort: [...], filters: [...] }     │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    QUERY PLANNER                             │
│    Input: user query (NL) + CapabilityMap                    │
│    Intent classifier (on-device TFLite)                      │
│    Output: ExecutionPlan { steps: [...], targetMetric: ... } │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│               NATIVE ACTION EXECUTOR                         │
│    Execute: sort(price_asc), filter(category), search(...)   │
│    Uses: AccessibilityNodeInfo.performAction()               │
│    Output: shortlisted product list (top N visible)          │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                    VALUE ENGINE                              │
│    Input: parsed product structs                             │
│    Compute: ₹/gram, ₹/ml, ₹/serving, effective price        │
│    Flag: fake discounts, misleading bundles                  │
│    Output: ranked list with computed metrics                 │
└────────────────────────┬────────────────────────────────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
    ┌─────────▼──────┐   ┌─────────▼────────────┐
    │  SIMPLE QUERY  │   │    COMPLEX QUERY       │
    │  Return ranked │   │  Call Cloud AI Layer   │
    │  result cards  │   │  (Claude API)          │
    └────────────────┘   └─────────┬────────────┘
                                   │
                         ┌─────────▼────────────┐
                         │   RESPONSE FORMATTER  │
                         │  Structured → Cards   │
                         └─────────┬────────────┘
                                   │
┌──────────────────────────────────▼──────────────────────────┐
│                     OVERLAY UI LAYER                         │
│              Jetpack Compose result cards                    │
│              Why? / Compare / Dismiss actions                │
└─────────────────────────────────────────────────────────────┘
```

---

## Layer Details

### Screen Understanding Layer

**Primary path — Accessibility API:**

```
onAccessibilityEvent(TYPE_WINDOW_CONTENT_CHANGED)
    → getRootInActiveWindow()
    → traverseNodeTree()
    → matchProductCardPattern()
    → extractFields(name, price, weight, discount, rating)
```

Product card detection uses a pattern matcher: repeated sibling nodes at the same depth with a price-like text child. This is more reliable than hardcoded selectors and handles layout variations.

**Fallback path — OCR:**

```
MediaProjection.createVirtualDisplay()
    → captureScreenshot()
    → ML Kit TextRecognizer.process(bitmap)
    → TextBlock segmentation
    → Price + weight regex extraction
```

OCR fallback activates when: (a) the Accessibility tree returns an empty root, (b) field extraction confidence is below threshold, or (c) the app is known to use a WebView.

---

### Action Discovery Engine

Scans the current Accessibility tree for interactive UI patterns:

```kotlin
data class CapabilityMap(
    val canSort: Boolean,
    val sortOptions: List<String>,
    val canFilter: Boolean,
    val filterOptions: List<String>,
    val hasSearchBar: Boolean,
    val hasPagination: Boolean
)
```

Node patterns looked for:
- Sort: spinner/dropdown with options containing "price", "popularity", "rating"
- Filter: checkbox groups, chip rows, side drawer
- Search: EditText with hint containing "search"

The CapabilityMap is produced before any query is planned. If the app has a sort-by-price option, that is always used before manually comparing parsed prices.

---

### Query Planner

Maps user intent to the cheapest execution path.

**Intent classification (TFLite, on-device):**

```
"best value toothpaste"
    → { objective: MINIMIZE_PRICE_PER_UNIT, category: TOOTHPASTE, constraint: null }

"healthiest cereal under ₹300"
    → { objective: MAXIMIZE_HEALTH_SCORE, category: CEREAL, constraint: { maxPrice: 300 } }
```

**Execution path selection:**

```
if (objective == MINIMIZE_PRICE_PER_UNIT && capabilityMap.canSort)
    → ExecutionPlan: [SORT(price_asc), EXTRACT_TOP_N(15), COMPUTE_UNIT_PRICE, RANK]

if (objective == MAXIMIZE_HEALTH_SCORE)
    → ExecutionPlan: [FILTER(category), EXTRACT_TOP_N(20), CALL_AI_REASONING]
```

The planner always prefers deterministic steps. AI is only called when the objective cannot be resolved by math alone.

---

### Value Engine

Pure functions. No AI. No I/O. Testable.

```kotlin
fun computeUnitPrice(price: Double, unit: NormalizedUnit): Double

fun detectFakeDiscount(
    currentPrice: Double,
    crossedOutPrice: Double,
    baselinePrice: Double?
): DiscountVerdict

fun estimateUsageDuration(
    weight: NormalizedUnit,
    category: ProductCategory
): Int  // days
```

Unit normalization converts all inputs to base SI units before any calculation:
- "200g", "0.2kg", "200 grams" → 200.0 grams
- "500ml", "0.5L", "500 millilitres" → 500.0 ml

This eliminates a class of comparison bugs.

---

### Cloud AI Layer

Called only when the query requires reasoning beyond math.

**Request payload (never includes raw screen data):**

```json
{
  "model": "claude-sonnet-4",
  "products": [
    {
      "name": "Colgate Strong Teeth",
      "price_inr": 112,
      "weight_g": 200,
      "price_per_gram": 0.56,
      "discount_verified": true,
      "rating": 4.2,
      "ingredients_available": false
    }
  ],
  "query_intent": {
    "objective": "MAXIMIZE_HEALTH_SCORE",
    "category": "TOOTHPASTE"
  },
  "user_preferences": {
    "budget_range": "mid",
    "avoid_ingredients": ["SLS"],
    "brand_affinity": []
  }
}
```

**Response parsed into:**

```kotlin
data class AIRankedResult(
    val rankedProducts: List<RankedProduct>,
    val explanation: String,
    val confidence: Float
)
```

Responses are cached by a hash of the product set + query intent for 15 minutes.

---

### Overlay UI Layer

The overlay is a `ComposeView` attached to `WindowManager` with type `TYPE_APPLICATION_OVERLAY`.

**State machine:**

```
HIDDEN → (user tap bubble) → LOADING → (result ready) → SHOWING_RESULTS
SHOWING_RESULTS → (user tap Why?) → SHOWING_EXPLANATION
SHOWING_RESULTS → (user dismiss) → HIDDEN
```

The panel never blocks the full screen. It occupies at most 60% of screen width on the side, allowing the user to still see and interact with the shopping app behind it.

---

## Data Flow Summary

```
User query
    → on-device intent classification (< 50ms)
    → screen reading via Accessibility API (< 200ms)
    → native sort/filter if available (< 300ms)
    → value engine math (< 50ms)
    → AI call if needed (< 1.2s)
    → result displayed in overlay
    
Total target: < 2 seconds
```

---

## Graceful Degradation

| Failure | Fallback Behavior |
|---------|-------------------|
| Accessibility tree unavailable | OCR via screenshot |
| OCR fails or low confidence | Show "Could not read products" with manual query option |
| AI call times out | Show rule-based ranked result with "AI reasoning unavailable" label |
| No sort/filter available in app | Parse visible products directly |
| Unit parsing fails | Show product but flag metric as "estimate" |
