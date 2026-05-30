# Master Plan — ValueLens

---

## Product Versions at a Glance

| Version | Theme | Target Users | Key Unlock |
|---------|-------|-------------|------------|
| V1 | Grocery copilot | Early adopters, students, budget shoppers | Price-per-unit intelligence |
| V2 | Smart shopping brain | Health-conscious users, families | Cross-app + nutrition + memory |
| V3 | Ambient commerce agent | Power users, professionals | Autonomous decisions + platform |

---

## V1 — Grocery Copilot

**Goal:** Prove the core loop. User opens a shopping app, triggers overlay, asks a question, gets a useful answer in under 2 seconds.

**Success Metric:** 60% of users who try it use it again within 7 days.

---

### Phase 1 — Foundation (Weeks 1–6)

#### Subphase 1.1 — Accessibility Layer
- Implement AccessibilityService in Android
- Build tree traversal for product card extraction
- Define node patterns for Zepto, Blinkit, Instamart layouts
- Handle A/B test UI variants gracefully

#### Subphase 1.2 — OCR Fallback
- Integrate ML Kit on-device OCR
- Screenshot capture via MediaProjection API
- Text region detection and layout parsing
- Confidence threshold: prefer Accessibility tree when available

#### Subphase 1.3 — Product Parser
- Regex + heuristic extraction of: name, price, weight string, discount label, quantity
- Unit normalizer: convert "1kg", "1000g", "1 L", "500ml" to base SI units
- Product card boundary detection
- Edge case handling: packs, combos, "X for ₹Y" formats

---

### Phase 2 — Intelligence Layer (Weeks 7–12)

#### Subphase 2.1 — Value Engine
- Implement deterministic ₹/gram, ₹/ml, ₹/serving calculators
- Discount verifier: effective price vs crossed-out MRP
- Usage duration estimator (rule-based per category)
- Ranking by value ratio

#### Subphase 2.2 — Action Discovery Engine
- Detect sort controls, filter panels, search bars in foreground app UI
- Build capability map per screen
- Map user query intent to cheapest available action

#### Subphase 2.3 — Query Planner
- Natural language intent classifier (on-device, lightweight model)
- Execution path selector: native action vs parse vs AI
- Top-N candidate shortlisting before AI call

#### Subphase 2.4 — AI Reasoning Integration
- Claude API integration for nuanced queries
- Structured prompt with parsed product JSON + user query
- Response parser → ranked result cards
- Fallback to rule-based output if AI call exceeds 1.5s

---

### Phase 3 — Overlay UI (Weeks 10–16)

#### Subphase 3.1 — Floating Button
- Persistent overlay button using WindowManager
- Draggable, remembers last position
- Tap to open panel, long-press for quick settings

#### Subphase 3.2 — Side Panel
- Slide-in panel over current app
- Result cards: rank, product name, key metric, explanation
- "Why?" expand → full breakdown
- "Compare" → side-by-side view

#### Subphase 3.3 — Query Input
- Bottom input bar with suggested queries
- Voice input support (uses on-device speech recognition)
- Recent queries remembered locally

---

### Phase 4 — V1 Hardening (Weeks 17–20)

- Supported apps: Zepto, Blinkit, Instamart, Amazon India
- Latency optimization: profile and reduce P90 to < 2s
- Crash-free rate target: 99.5%
- Beta testing with 200 users
- Onboarding flow and permission explanation screens
- Play Store submission

---

## V2 — Smart Shopping Brain

**Goal:** Make ValueLens indispensable. It knows your preferences, compares across apps, and gives health-aware recommendations.

**Success Metric:** 30% of users use it 4+ times per week.

---

### Phase 5 — Cross-App Comparison (Weeks 21–28)

#### Subphase 5.1 — Price Memory Cache
- Store per-product prices from recent queries (on-device, encrypted)
- Baseline price database per SKU (toothpaste, shampoo, protein, etc.)
- Comparison trigger: "Also ₹28 cheaper on Blinkit right now"

#### Subphase 5.2 — App Switcher Intelligence
- Detect which commerce apps are installed
- Deep link to equivalent product on alternative app
- Price delta surfacing in overlay

---

### Phase 6 — Nutrition Intelligence (Weeks 24–30)

#### Subphase 6.1 — Ingredient Database
- Curated ingredient quality database (food additives, harmful preservatives, palm oil, high-fructose corn syrup, etc.)
- Abrasive score for dental products (toothpaste RDA index)
- Ultra-processed food classifier

#### Subphase 6.2 — Nutrition Scoring
- Macro extraction from product images + labels via vision model
- Protein/₹, fiber/₹, sugar alert
- Composite health score per category

---

### Phase 7 — Personalized Memory (Weeks 28–34)

#### Subphase 7.1 — Preference Engine
- On-device preference graph: budget range, avoided ingredients, preferred brands
- Updated passively from past query choices
- Explicit preference setting in app settings

#### Subphase 7.2 — Personalized Scoring
- Adjust ranking weights based on user history
- "Based on your past choices, you prefer low-sugar, mid-range budget"
- Preference reset and audit UI

---

## V3 — Ambient Commerce Agent

**Goal:** ValueLens becomes a proactive, autonomous shopping intelligence layer.

**Success Metric:** Subscription conversion > 15%, $5–10/month pricing viable.

---

### Phase 8 — Autonomous Agent Mode (Weeks 35–48)

#### Subphase 8.1 — Agent Runtime
- Multi-step task execution: "Find best protein powder under ₹2000 on Blinkit"
- Agent can scroll, sort, filter, and collect product data autonomously
- Step-by-step execution log visible to user (trust + transparency)

#### Subphase 8.2 — Cart Intelligence
- Monitor cart contents in real time
- Surface per-item alternatives: "This ghee is 22% costlier per gram than the Blinkit alternative"
- Total cart value score

#### Subphase 8.3 — Scheduled Intelligence
- Weekly grocery intelligence report: how much did you overpay? what patterns exist?
- Price drop alerts for saved products

---

### Phase 9 — Platform Expansion (Weeks 40–52)

- Expand beyond groceries: medicine, electronics, restaurant menus, SaaS pricing
- Generalized mobile action graph: learn interaction patterns per app category
- iOS research: test feasibility via Screen Time API and Share Extension
- Web companion: Chrome extension for desktop shopping (Flipkart, Amazon web)

---

## Timeline Overview

```
Weeks 1–6    Phase 1   Foundation (Accessibility + OCR + Parser)
Weeks 7–12   Phase 2   Intelligence (Value Engine + Query Planner + AI)
Weeks 10–16  Phase 3   Overlay UI (overlaps with Phase 2)
Weeks 17–20  Phase 4   V1 Hardening + Play Store
Weeks 21–34  Phase 5–7 V2 Features
Weeks 35–52  Phase 8–9 V3 Agent + Platform
```

---

## Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Google Play Accessibility policy violation | High | Follow declared-use guidelines; do not automate purchases in V1 |
| Target app UI changes breaking parser | Medium | Parser versioning + remote config for node selectors |
| AI latency exceeds 2s | Medium | Fallback to rule-based output; cache common queries |
| Wrong recommendation (hallucination) | High | All math is deterministic; AI only ranks, never calculates |
| User trust gap around screen reading | High | Clear permission explanations; local-first data model |
