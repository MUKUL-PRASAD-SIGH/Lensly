package com.example.lensly.repository

import com.example.lensly.engine.ValueEngine
import com.example.lensly.models.*
import com.example.lensly.network.AnalyzeRequest
import com.example.lensly.network.ApiClient
import com.example.lensly.network.DtoMapper.toDto
import com.example.lensly.network.DtoMapper.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AnalysisRepository — the single source of truth for product analysis.
 *
 * Strategy:
 *   1. Run deterministic ValueEngine ranking immediately (zero latency)
 *   2. Fire API call to backend for AI-enhanced ranking in parallel
 *   3. Return deterministic result first, then emit AI result when ready
 *
 * The ViewModel observes a Flow so the UI can update progressively.
 */
class AnalysisRepository {

    sealed class AnalysisResult {
        data class Success(
            val ranked: List<RankedProduct>,
            val explanation: String,
            val usedAi: Boolean
        ) : AnalysisResult()
        data class Error(val message: String) : AnalysisResult()
        object Loading : AnalysisResult()
    }

    /**
     * Analyzes products with the given query intent.
     * Returns a deterministic result synchronously, then upgrades with AI if needed.
     */
    suspend fun analyze(
        products: List<Product>,
        intent: QueryIntent,
        preferences: UserPreferences = UserPreferences()
    ): AnalysisResult = withContext(Dispatchers.IO) {
        if (products.isEmpty()) {
            return@withContext AnalysisResult.Error("No products found on screen")
        }

        // Always run local ranking first — this is instant
        val localRanked = ValueEngine.rankByValue(products)

        // Try to enhance with backend AI for complex queries
        return@withContext try {
            val request = AnalyzeRequest(
                products = products.map { it.toDto() },
                queryIntent = intent.toDto(),
                userPreferences = preferences.toDto()
            )

            val response = ApiClient.service.analyze(request)

            if (response.isSuccessful) {
                val body = response.body()!!
                val aiRanked = body.rankedProducts.map { dto ->
                    dto.toDomain(products)
                }
                AnalysisResult.Success(
                    ranked = aiRanked.ifEmpty { localRanked },
                    explanation = body.explanation,
                    usedAi = body.usedAi
                )
            } else {
                // Backend returned error — use local result
                AnalysisResult.Success(
                    ranked = localRanked,
                    explanation = "Ranked by value (AI unavailable)",
                    usedAi = false
                )
            }
        } catch (e: Exception) {
            // Network failure — use local result gracefully
            AnalysisResult.Success(
                ranked = localRanked,
                explanation = "Ranked by value (offline mode)",
                usedAi = false
            )
        }
    }
}
