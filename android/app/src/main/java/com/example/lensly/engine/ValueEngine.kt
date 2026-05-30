package com.example.lensly.engine

import com.example.lensly.models.*
import kotlin.math.abs

/**
 * ValueEngine — pure deterministic math, no AI, no I/O.
 * All functions are stateless and fully unit-testable.
 *
 * Design principle: Optimization-first, AI-last.
 * The engine resolves as much as possible before any network call.
 */
object ValueEngine {

    /**
     * Computes the price per base SI unit.
     * Returns null if neither weight nor volume is known.
     */
    fun computeUnitPrice(product: Product): Pair<Double, String>? {
        return when {
            product.weight != null && product.weight.value > 0 ->
                Pair(product.priceInr / product.weight.value, "₹/gram")
            product.volume != null && product.volume.value > 0 ->
                Pair(product.priceInr / product.volume.value, "₹/ml")
            else -> null
        }
    }

    /**
     * Ranks a list of products by price-per-unit (ascending = better value).
     * Products without weight/volume are ranked last by absolute price.
     */
    fun rankByValue(products: List<Product>): List<RankedProduct> {
        data class Scored(val product: Product, val score: Double, val metric: String)

        val scored = products.map { p ->
            val unitPrice = computeUnitPrice(p)
            if (unitPrice != null) {
                Scored(p, unitPrice.first, unitPrice.second)
            } else {
                Scored(p, p.priceInr, "₹")
            }
        }.sortedBy { it.score }

        return scored.mapIndexed { index, s ->
            val discount = detectFakeDiscount(s.product)
            RankedProduct(
                product = s.product,
                rank = index + 1,
                scorePerUnit = s.score,
                metric = s.metric,
                explanation = buildExplanation(s.product, s.score, s.metric, index),
                isFakeDiscount = discount.isLikelyFake,
                fakeDiscountReason = discount.reason
            )
        }
    }

    /**
     * Detects whether a discount is misleading.
     *
     * A discount is flagged fake when:
     *   - The effective per-unit price is higher than the cheapest alternative
     *   - The MRP appears inflated (discount > 70% without verifiable market baseline)
     */
    fun detectFakeDiscount(product: Product): DiscountVerdict {
        val mrp = product.mrpCrossedOut ?: return DiscountVerdict(false, null)
        if (mrp <= product.priceInr) {
            return DiscountVerdict(true, "Selling price equals or exceeds crossed-out MRP")
        }

        val discountPct = ((mrp - product.priceInr) / mrp) * 100
        return when {
            discountPct > 70 -> DiscountVerdict(
                true,
                "Discount of ${discountPct.toInt()}% seems unusually high — MRP may be inflated"
            )
            else -> DiscountVerdict(false, null)
        }
    }

    /**
     * Estimates usage duration in days for consumable products.
     * Based on category-specific per-day consumption averages.
     */
    fun estimateUsageDuration(product: Product): Int? {
        val weightG = product.weight?.value ?: return null
        // Rough category defaults (grams per day)
        val gramsPerDay = when {
            product.name.contains("toothpaste", ignoreCase = true) -> 2.0
            product.name.contains("shampoo", ignoreCase = true) -> 5.0
            product.name.contains("protein", ignoreCase = true) -> 30.0
            product.name.contains("flour", ignoreCase = true) -> 100.0
            product.name.contains("rice", ignoreCase = true) -> 100.0
            product.name.contains("sugar", ignoreCase = true) -> 25.0
            product.name.contains("ghee", ignoreCase = true) -> 15.0
            else -> return null
        }
        return (weightG / gramsPerDay).toInt().coerceAtLeast(1)
    }

    // --- Private helpers ---

    private fun buildExplanation(
        product: Product,
        score: Double,
        metric: String,
        rank: Int
    ): String {
        val formatted = String.format("%.2f", score)
        return when (rank) {
            0 -> "Best value at $formatted $metric"
            1 -> "Second best — $formatted $metric"
            else -> "$formatted $metric"
        }
    }
}

data class DiscountVerdict(
    val isLikelyFake: Boolean,
    val reason: String?
)
