package com.example.lensly.ui.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lensly.models.*
import com.example.lensly.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * OverlayViewModel — drives the overlay panel UI state.
 *
 * State machine:
 *   IDLE → LOADING → RESULTS (or ERROR)
 *   RESULTS → EXPLANATION (when user taps "Why?")
 *   Any state → IDLE (on dismiss)
 */
class OverlayViewModel : ViewModel() {

    private val repository = AnalysisRepository()

    private val _uiState = MutableStateFlow<OverlayUiState>(OverlayUiState.Idle)
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    private var _lastProducts: List<Product> = emptyList()

    /** Called when user triggers overlay while on a shopping app. */
    fun analyze(products: List<Product>, rawQuery: String = "best value") {
        if (products.isEmpty()) {
            _uiState.value = OverlayUiState.Error("No products detected on screen")
            return
        }
        _lastProducts = products
        _uiState.value = OverlayUiState.Loading

        val intent = buildIntent(rawQuery)

        viewModelScope.launch {
            val result = repository.analyze(products, intent)
            _uiState.value = when (result) {
                is AnalysisRepository.AnalysisResult.Success -> OverlayUiState.Results(
                    ranked = result.ranked,
                    explanation = result.explanation,
                    usedAi = result.usedAi
                )
                is AnalysisRepository.AnalysisResult.Error ->
                    OverlayUiState.Error(result.message)
                AnalysisRepository.AnalysisResult.Loading ->
                    OverlayUiState.Loading
            }
        }
    }

    fun showExplanation(product: RankedProduct) {
        val current = _uiState.value as? OverlayUiState.Results ?: return
        _uiState.value = OverlayUiState.Explanation(
            product = product,
            previousResults = current
        )
    }

    fun backToResults() {
        val current = _uiState.value as? OverlayUiState.Explanation ?: return
        _uiState.value = current.previousResults
    }

    fun dismiss() {
        _uiState.value = OverlayUiState.Idle
    }

    private fun buildIntent(rawQuery: String): QueryIntent {
        val objective = when {
            rawQuery.contains("health", ignoreCase = true) -> Objective.MAXIMIZE_HEALTH_SCORE
            rawQuery.contains("cheap", ignoreCase = true) ||
                rawQuery.contains("price", ignoreCase = true) -> Objective.MINIMIZE_PRICE_PER_UNIT
            rawQuery.contains("last", ignoreCase = true) -> Objective.LONGEST_DURATION
            else -> Objective.BEST_VALUE
        }
        return QueryIntent(
            objective = objective,
            category = "general",
            rawQuery = rawQuery
        )
    }
}

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

sealed class OverlayUiState {
    object Idle : OverlayUiState()
    object Loading : OverlayUiState()

    data class Results(
        val ranked: List<RankedProduct>,
        val explanation: String,
        val usedAi: Boolean
    ) : OverlayUiState()

    data class Explanation(
        val product: RankedProduct,
        val previousResults: Results
    ) : OverlayUiState()

    data class Error(val message: String) : OverlayUiState()
}
