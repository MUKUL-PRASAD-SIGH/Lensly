package com.example.lensly.planner

import com.example.lensly.models.Objective
import com.example.lensly.models.QueryIntent
import java.util.regex.Pattern

/**
 * Fallback RegexIntentClassifier that matches shopping-specific keywords to classify queries.
 */
class RegexIntentClassifier : IntentClassifier {

    override fun classify(rawQuery: String): QueryIntent {
        val query = rawQuery.trim().lowercase()

        // 1. Determine Objective
        val objective = when {
            // FIND_CHEAPEST keywords
            query.contains("cheap") || query.contains("lowest") || query.contains("minimum") || query.contains("least cost") -> 
                Objective.MINIMIZE_PRICE_PER_UNIT

            // DEAL_SEARCH keywords
            query.contains("discount") || query.contains("deal") || query.contains("offer") || query.contains("sale") -> 
                Objective.DETECT_FAKE_DISCOUNT

            // BEST_RATED keywords
            query.contains("rate") || query.contains("rating") || query.contains("star") || query.contains("reviews") || query.contains("top") -> 
                Objective.BEST_OVERALL

            // BUDGET_SEARCH keywords
            query.contains("budget") || query.contains("under") || query.contains("below") -> 
                Objective.MINIMIZE_PRICE_PER_UNIT

            // MAXIMIZE_HEALTH_SCORE keywords
            query.contains("health") || query.contains("organic") || query.contains("ingredients") || query.contains("nutri") || query.contains("sugar") -> 
                Objective.MAXIMIZE_HEALTH_SCORE

            // COMPARE_PRODUCTS keywords
            query.contains("compare") || query.contains("versus") || query.contains("vs") || query.contains("better") || query.contains("difference") -> 
                Objective.BEST_OVERALL

            // VALUE_FOR_MONEY keywords
            query.contains("value") || query.contains("worth") || query.contains("quantity") || query.contains("per gram") -> 
                Objective.BEST_VALUE

            else -> Objective.BEST_VALUE
        }

        // 2. Extract Budget Constraint (e.g. "under 50", "below 100", "budget 500")
        var maxPrice: Double? = null
        val budgetPattern = Pattern.compile("(?:under|below|budget|rs\\.?|rupees?\\.?)\\s*(\\d+)")
        val matcher = budgetPattern.matcher(query)
        if (matcher.find()) {
            try {
                maxPrice = matcher.group(1)?.toDouble()
            } catch (e: Exception) {
                // Ignore parse failures
            }
        }

        // 3. Extract Category (scanning right-to-left for the first non-numeric, non-noise keyword)
        val noiseWords = setOf(
            "cheap", "cheapest", "lowest", "minimum", "least", "cost", "price",
            "discount", "deal", "deals", "offer", "offers", "sale", "rate", "rating",
            "ratings", "star", "stars", "reviews", "review", "top", "budget", "under",
            "below", "health", "healthy", "healthiest", "organic", "ingredients",
            "nutri", "sugar", "compare", "versus", "vs", "better", "difference",
            "value", "worth", "quantity", "grams", "gram", "ml", "litres", "litre",
            "kg", "g", "l", "rs", "rupee", "rupees", "inr", "bucks", "on", "for", "the",
            "show", "me", "find", "get", "of", "and", "a", "an", "is"
        )
        val words = query.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        var category = "general"
        for (i in words.indices.reversed()) {
            val w = words[i]
            // Skip numeric/decimal values
            if (w.toDoubleOrNull() != null) continue
            // Skip noise words
            if (w in noiseWords) continue
            // First valid word is our category
            if (w.all { it.isLetter() }) {
                category = w
                break
            }
        }

        return QueryIntent(
            objective = objective,
            category = category,
            maxPriceInr = maxPrice,
            rawQuery = rawQuery
        )
    }
}
