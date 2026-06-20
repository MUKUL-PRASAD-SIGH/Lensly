# Verification Log: Blinkit Mock Screen Parsing & Intent Classification

This document logs the integration testing for simulated grocery search parsing on Blinkit-like UI trees, and the corresponding regex-based classification enhancements.

---

## 🛒 The Mock Blinkit Screen Simulation

We constructed a mock Blinkit screen text dump inside [BlinkitScreenTest.kt](file:///c:/Users/Mukul%20Prasad/Desktop/PROJECTS/Lensly/android/app/src/test/java/com/example/lensly/parser/BlinkitScreenTest.kt) to test our product extractor (`ProductParser`) and unit normalizer (`UnitNormalizer`).

### Mock Product Cards Simulated:
1. **Amul Taaza T-Special Homogenized Toned Milk**
   * Volume: `1 L` (Expected Normalization: `1000.0 mL`)
   * Price: `₹74` (MRP: `₹78`)
   * Discount: `5% OFF`
2. **Mother Dairy Premium Toned Milk**
   * Volume: `500 ml` (Expected Normalization: `500.0 mL`)
   * Price: `₹28` (MRP: `₹28`)
3. **Nandini Toned Fresh Milk**
   * Volume: `200 ml` (Expected Normalization: `200.0 mL`)
   * Price: `₹12` (MRP: `₹15`)
   * Discount: `20% OFF`
4. **Organic India Cow Milk (Super Premium)**
   * Volume: `1 L` (Expected Normalization: `1000.0 mL`)
   * Price: `₹140` (MRP: `₹140`)

---

## 🔬 Test Case Results

### 1. Product Feature Parsing & Normalization
* **Parsed Price:** Extracted lowest parsed prices (e.g. `₹74.0` for Amul, `₹28.0` for Mother Dairy).
* **Parsed MRP:** Correctly extractedcrossed-out MRPs or fallback prices.
* **Volume Conversion:** Normalised liters to milliliters successfully (e.g., `1 L` -> `1000 ml`).

### 2. Value Engine Sorting Ranking
* Ranks products by price-per-unit (ascending order):
  1. **Mother Dairy Premium Toned Milk** — `₹0.056 / mL` (Rank 1)
  2. **Nandini Toned Fresh Milk** — `₹0.060 / mL` (Rank 2)
  3. **Amul Taaza T-Special** — `₹0.074 / mL` (Rank 3)
  4. **Organic India Cow Milk** — `₹0.140 / mL` (Rank 4)
* **Status:** Passed.

### 3. Regex Query Intent Extraction
* Tested budget constraints (`milk under 80 rupees`):
  * **Objective:** `MINIMIZE_PRICE_PER_UNIT`
  * **Max Price:** `80.0`
  * **Category:** `milk`
* Tested discount intents (`best discount on chocolate`):
  * **Objective:** `DETECT_FAKE_DISCOUNT`
  * **Category:** `chocolate`
* Tested health conscious intents (`healthiest cooking oil`):
  * **Objective:** `MAXIMIZE_HEALTH_SCORE`
  * **Category:** `oil`
* **Status:** Passed.

---

## 🔧 Regex Category Extraction Improvement
To handle suffix units like "rupees", "rs", "inr", etc. or numeric values at the end of the query without misclassifying them as categories, we upgraded the classification scanning strategy:
* Splits the query into tokens.
* Scans **right-to-left (backwards)**.
* Automatically skips numeric tokens and common shopping/currency noise words (e.g., `"rupees"`, `"rs"`, `"under"`).
* Extracts the first meaningful letter-based token as the target category.
