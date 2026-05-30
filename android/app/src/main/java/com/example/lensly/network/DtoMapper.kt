package com.example.lensly.network

import com.example.lensly.models.*

/**
 * Mapper functions between domain models and network DTOs.
 * Keeps the domain layer clean — models never import network types.
 */
object DtoMapper {

    fun Product.toDto() = ProductDto(
        name = name,
        priceInr = priceInr,
        weightG = weight?.value,
        volumeMl = volume?.value,
        mrpCrossedOut = mrpCrossedOut,
        discountPercent = null,
        rating = rating?.toDouble(),
        sourceApp = sourceApp.name.lowercase()
    )

    fun QueryIntent.toDto() = QueryIntentDto(
        objective = objective.name,
        category = category,
        maxPrice = maxPriceInr,
        rawQuery = rawQuery
    )

    fun UserPreferences.toDto() = UserPreferencesDto(
        budgetRange = budgetRange.name.lowercase(),
        avoidIngredients = avoidIngredients,
        brandAffinity = brandAffinity
    )

    fun RankedProductDto.toDomain(originalProducts: List<Product>): RankedProduct {
        val product = originalProducts.find { it.name == name }
            ?: Product(name = name, priceInr = 0.0)
        return RankedProduct(
            product = product,
            rank = rank,
            scorePerUnit = score,
            metric = metric,
            explanation = explanation,
            isFakeDiscount = fakeDiscountWarning ?: false
        )
    }
}
