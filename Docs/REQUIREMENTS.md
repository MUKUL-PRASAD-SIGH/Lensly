# Requirements — ValueLens

---

## 1. Functional Requirements

### 1.1 Screen Reading

- The app must read content from any foreground app using Android Accessibility Services
- When the Accessibility tree is unavailable or insufficient, fall back to OCR via screenshot
- Extract product name, price, weight/volume, quantity, discount label, and rating from visible UI
- Operate without any API integration with the target shopping app

### 1.2 Action Discovery

- Detect available interactive elements on the current screen: sort dropdowns, filter panels, search bars, category tabs, and pagination controls
- Build a capability map of the current screen before executing any query
- Prefer native app operations over manual parsing wherever possible

### 1.3 Query Execution

- Accept natural language queries from the user
- Route queries through a planner that selects the lowest-cost execution path
- Execute native app operations (sort, filter) when available before falling back to parsing
- Parse only the top-N shortlisted candidates, not the full product listing

### 1.4 Value Computation

- Compute the following metrics deterministically (no AI):
  - ₹ per gram
  - ₹ per ml
  - ₹ per serving (where serving size is available)
  - Protein per rupee (for food/supplement categories)
  - Calories per rupee
  - Effective price after discount
  - Estimated usage duration (for toothpaste, shampoo, etc.)
- Flag discounts where the effective per-unit price is higher than apparent alternatives

### 1.5 AI Reasoning

- Handle subjective or multi-factor queries: "healthiest", "best overall", "worth the extra cost"
- Analyze ingredient lists when available
- Rank products using a configurable scoring model
- Return results with a plain-language explanation

### 1.6 Overlay UI

- Appear as a non-intrusive overlay on top of the foreground app
- Activate via a persistent floating button, edge swipe, or quick settings tile
- Display ranked results in a compact card format
- Support "Why?" and "Compare" actions per result
- Dismiss cleanly without disrupting the user's position in the shopping app

### 1.7 Fake Discount Detection

- Identify cases where the crossed-out MRP appears artificially inflated
- Compare effective per-unit price against market baseline (from cached data or recent queries)
- Surface a warning label: "Discount may be misleading"

---

## 2. Non-Functional Requirements

### 2.1 Latency

- Target end-to-end response time: under 2 seconds for standard queries
- On-device parsing and math must complete in under 400ms
- Cloud AI call must complete in under 1.2 seconds (P90)
- Overlay must appear and be interactive within 300ms of trigger

### 2.2 Accuracy

- Unit price calculations must be deterministically correct — never AI-generated
- Product extraction must achieve >90% field accuracy on supported apps (Zepto, Blinkit, Amazon)
- AI reasoning output must include confidence signal; low-confidence results are labeled

### 2.3 Privacy

- No product data, query text, or screen content is stored server-side beyond the duration of a single request
- User preferences and history are stored locally on-device only (encrypted)
- No third-party analytics SDK in V1

### 2.4 Reliability

- App must not crash or destabilize the foreground shopping app
- Accessibility service must handle unexpected UI changes gracefully (app update, A/B test variant)
- Overlay must not intercept touches intended for the underlying app when dismissed

### 2.5 Battery and Performance

- Background CPU usage when idle: < 1% average
- Memory footprint of resident service: < 50MB
- Screenshot capture and OCR pipeline must not cause noticeable lag in the foreground app

### 2.6 Compatibility

- Minimum Android version: Android 9 (API level 28)
- Target Android version: Android 14 (API level 34)
- Must function correctly with Android's battery optimization without requiring the user to whitelist manually in V1
- Overlay must render correctly on screen sizes from 5.0" to 7.0" and at font scale up to 1.3x

---

## 3. Supported Apps — V1

| App | Accessibility Support | OCR Fallback |
|-----|-----------------------|--------------|
| Zepto | Yes | Yes |
| Blinkit | Yes | Yes |
| Swiggy Instamart | Yes | Yes |
| Amazon India | Partial | Yes |
| JioMart | OCR only | Yes |

---

## 4. Query Types — V1

| Query | Resolution Method |
|-------|-------------------|
| "best price to weight ratio" | Deterministic math |
| "cheapest per gram" | Sort + math |
| "fake discount" | Baseline comparison |
| "healthiest option" | AI + ingredient DB |
| "best protein per rupee" | Math |
| "best value under ₹300" | Filter + math |
| "best overall" | AI scoring |
| "which lasts longest" | Usage estimate model |

---

## 5. Out of Scope — V1

- Autonomous checkout or cart modification
- Voice activation
- iOS support
- Price tracking over time
- Cross-app comparison (V2)
- Personalized memory (V2)
