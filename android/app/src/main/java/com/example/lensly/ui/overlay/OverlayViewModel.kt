package com.example.lensly.ui.overlay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lensly.models.*
import com.example.lensly.repository.AnalysisRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModelProvider
import com.example.lensly.db.QueryHistoryDao
import com.example.lensly.db.QueryHistoryEntity
import com.example.lensly.planner.IntentClassifier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * OverlayViewModel — drives the overlay panel UI state.
 *
 * State machine:
 *   IDLE → LOADING → RESULTS (or ERROR)
 *   RESULTS → EXPLANATION (when user taps "Why?")
 *   Any state → IDLE (on dismiss)
 */
class OverlayViewModel(
    private val queryHistoryDao: QueryHistoryDao,
    private val intentClassifier: IntentClassifier
) : ViewModel() {

    private val repository = AnalysisRepository()

    private val _uiState = MutableStateFlow<OverlayUiState>(OverlayUiState.Idle)
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    val recentQueries: StateFlow<List<String>> = queryHistoryDao.getRecentQueries()
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    queryHistoryDao.insertQuery(QueryHistoryEntity(query = rawQuery, timestampMs = System.currentTimeMillis()))
                    queryHistoryDao.deleteOldQueries()
                }
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
            } catch (e: Exception) {
                _uiState.value = OverlayUiState.Error("Analysis failed: ${e.message}")
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

    fun startLoading() {
        _uiState.value = OverlayUiState.Loading
    }

    fun showError(message: String) {
        _uiState.value = OverlayUiState.Error(message)
    }

    fun dismiss() {
        _uiState.value = OverlayUiState.Idle
    }

    private fun buildIntent(rawQuery: String): QueryIntent {
        return intentClassifier.classify(rawQuery)
    }

    class Factory(
        private val dao: QueryHistoryDao,
        private val classifier: IntentClassifier
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OverlayViewModel(dao, classifier) as T
        }
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
