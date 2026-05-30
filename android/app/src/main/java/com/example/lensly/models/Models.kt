package com.example.lensly.models

/**
 * Represents a product parsed from a shopping app screen.
 * All values are extracted on-device — never raw screen data sent to backend.
 */
data class Product(
    val name: String,
    val priceInr: Double,
    val weight: NormalizedUnit? = null,      // e.g. 200g
    val volume: NormalizedUnit? = null,      // e.g. 500ml
    val mrpCrossedOut: Double? = null,       // Strikethrough price
    val discountLabel: String? = null,       // "52% off"
    val rating: Float? = null,
    val sourceApp: SourceApp = SourceApp.UNKNOWN,
    val rawWeightString: String? = null      // Original string before normalization
)

/**
 * A quantity with its SI unit after normalization.
 * Example: "1kg" → NormalizedUnit(1000.0, SIUnit.GRAM)
 */
data class NormalizedUnit(
    val value: Double,
    val unit: SIUnit
)

enum class SIUnit { GRAM, MILLILITER, UNIT }

/** Shopping apps we support in V1. */
enum class SourceApp(val packageName: String) {
    ZEPTO("com.application.zomato"),
    BLINKIT("com.blinkit.consumer"),
    INSTAMART("in.swiggy.android"),
    AMAZON("com.amazon.mShop.android.shopping"),
    JIOMART("com.jiomart.android"),
    UNKNOWN("")
}

/**
 * A Product with ranking metadata computed by ValueEngine or AI.
 */
data class RankedProduct(
    val product: Product,
    val rank: Int,
    val scorePerUnit: Double,       // ₹/gram or ₹/ml — lower is better
    val metric: String,             // "₹/gram", "₹/ml", "₹"
    val explanation: String,
    val isFakeDiscount: Boolean = false,
    val fakeDiscountReason: String? = null
)

/**
 * Structured intent produced by on-device classification.
 */
data class QueryIntent(
    val objective: Objective,
    val category: String,           // "toothpaste", "protein_powder"
    val maxPriceInr: Double? = null,
    val rawQuery: String = ""
)

enum class Objective {
    MINIMIZE_PRICE_PER_UNIT,
    MAXIMIZE_HEALTH_SCORE,
    BEST_OVERALL,
    BEST_VALUE,
    DETECT_FAKE_DISCOUNT,
    LONGEST_DURATION,
    INGREDIENT_ANALYSIS
}

/**
 * Anonymized user preferences stored on-device only.
 */
data class UserPreferences(
    val budgetRange: BudgetRange = BudgetRange.MID,
    val avoidIngredients: List<String> = emptyList(),
    val brandAffinity: List<String> = emptyList()
)

enum class BudgetRange { LOW, MID, HIGH }
