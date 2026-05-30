package com.example.lensly.network

import com.example.lensly.models.Product
import com.example.lensly.models.QueryIntent
import com.example.lensly.models.RankedProduct
import com.example.lensly.models.UserPreferences
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// ---------------------------------------------------------------------------
// Request / Response DTOs
// ---------------------------------------------------------------------------

data class AnalyzeRequest(
    @SerializedName("products") val products: List<ProductDto>,
    @SerializedName("query_intent") val queryIntent: QueryIntentDto,
    @SerializedName("user_preferences") val userPreferences: UserPreferencesDto
)

data class AnalyzeResponse(
    @SerializedName("ranked_products") val rankedProducts: List<RankedProductDto>,
    @SerializedName("explanation") val explanation: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("used_ai") val usedAi: Boolean
)

data class ProductDto(
    @SerializedName("name") val name: String,
    @SerializedName("price_inr") val priceInr: Double,
    @SerializedName("weight_g") val weightG: Double?,
    @SerializedName("volume_ml") val volumeMl: Double?,
    @SerializedName("mrp_crossed_out") val mrpCrossedOut: Double?,
    @SerializedName("discount_percent") val discountPercent: Double?,
    @SerializedName("rating") val rating: Double?,
    @SerializedName("source_app") val sourceApp: String
)

data class RankedProductDto(
    @SerializedName("name") val name: String,
    @SerializedName("rank") val rank: Int,
    @SerializedName("score") val score: Double,
    @SerializedName("metric") val metric: String,
    @SerializedName("explanation") val explanation: String,
    @SerializedName("fake_discount_warning") val fakeDiscountWarning: Boolean?
)

data class QueryIntentDto(
    @SerializedName("objective") val objective: String,
    @SerializedName("category") val category: String,
    @SerializedName("max_price") val maxPrice: Double?,
    @SerializedName("raw_query") val rawQuery: String
)

data class UserPreferencesDto(
    @SerializedName("budget_range") val budgetRange: String,
    @SerializedName("avoid_ingredients") val avoidIngredients: List<String>,
    @SerializedName("brand_affinity") val brandAffinity: List<String>
)

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String
)

// ---------------------------------------------------------------------------
// Retrofit Interface
// ---------------------------------------------------------------------------

interface LenslyApiService {

    @GET("/health")
    suspend fun health(): Response<HealthResponse>

    /**
     * Main endpoint: send parsed products + user query, get ranked results.
     * Backend routes to Claude AI or deterministic math based on query objective.
     */
    @POST("/api/v1/analyze")
    suspend fun analyze(@Body request: AnalyzeRequest): Response<AnalyzeResponse>

    /**
     * Fast deterministic ranking only — no AI, guaranteed < 200ms.
     */
    @POST("/api/v1/rank")
    suspend fun rank(@Body request: AnalyzeRequest): Response<AnalyzeResponse>
}
