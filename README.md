# ValueLens

> **AI-powered mobile overlay that reads your shopping screen and tells you the smartest product to buy — instantly.**

---

## What Is ValueLens?

ValueLens is an Android overlay app that activates via a floating button or shortcut while you browse Zepto, Blinkit, Amazon, or any commerce app. It reads what's on your screen, extracts product data, and gives you an instant ranked recommendation — without you copying anything, switching apps, or doing mental math.

**One-line pitch:** Press a shortcut anywhere on your phone. AI instantly tells you the best product to buy and why.

---

## The Problem It Solves

Mobile shopping apps are not built to help you compare. They are built to make you buy. Hidden unit economics, fake discounts, and infinite scroll all work against the user.

Humans are bad at computing on the fly:
- ₹ per gram across 8 products
- Whether a "Buy 2 Get 1" is actually cheaper per unit
- Which protein powder has the best macros per rupee
- Whether the 52% discount is real or the MRP was inflated

ValueLens solves this entirely. It compresses a 5-minute comparison into 5 seconds.

---

## How It Works — User Flow

```
User opens Zepto / Blinkit / Amazon
        ↓
Taps floating ValueLens bubble (or uses quick-tile shortcut)
        ↓
Overlay panel slides in from the side
        ↓
App reads the current screen via Accessibility API
        ↓
Action Discovery Engine checks: can I sort? filter? search?
        ↓
Executes cheapest native operations first (sort by price, filter category)
        ↓
Parses only the top visible candidates (not the whole catalog)
        ↓
Value Engine computes: ₹/gram, ₹/ml, protein/₹, etc.
        ↓
AI Reasoning Layer handles nuanced query (healthiest, best overall)
        ↓
Overlay displays ranked result with explanation
        ↓
User taps "Why?" or "Compare" for deeper breakdown
```

---

## Document Index

| File | Contents |
|------|----------|
| `README.md` | This file. Overview, flow, and index |
| `REQUIREMENTS.md` | Full functional and non-functional requirements |
| `MASTER_PLAN.md` | Phased roadmap: phases, subphases, V1/V2/V3 |
| `TECH_STACK.md` | Every technology choice with justification |
| `ARCHITECTURE.md` | System design, layers, data flow, query planner |
| `SECURITY.md` | Privacy model, data handling, threat model |
| `PERMISSIONS.md` | Android permissions — what, why, and user trust |
| `DEPLOYMENT.md` | Build pipeline, release process, distribution |
| `AI_IDE_SETUP.md` | AI-powered development environment configuration |
| `HOW_TO_USE.md` | End-user guide: install, activate, query on phone |

---

## Version Summary

| Version | Focus | Status |
|---------|-------|--------|
| V1 | Grocery overlay, price-per-unit, fake discount detection | MVP |
| V2 | Cross-app comparison, nutrition intelligence, memory | Growth |
| V3 | Autonomous agent, personalized scoring, platform expansion | Scale |

---

## Why This Is a Strong Product

- **Universal pain point** — everyone shops and everyone compares
- **Instantly demonstrable** — value is visible in 5 seconds
- **High frequency** — groceries are weekly, habit-forming
- **India-first** — Android-dominant market, high price sensitivity, discount-heavy UX
- **Viral demo potential** — "AI exposed a fake discount" is a Reels goldmine

---

## Design Philosophy

ValueLens is not a chatbot. It is a **contextual decision runtime** layered on top of commerce apps.

The architecture principle is **optimization-first, AI-last**:
1. Use app-native operations (sort, filter, search) first
2. Apply deterministic math for unit economics
3. Invoke AI only for nuanced reasoning

This keeps latency under 2 seconds and cost manageable at scale.
