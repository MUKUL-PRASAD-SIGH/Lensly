package com.example.lensly.planner

import android.content.Context
import android.util.Log
import com.example.lensly.models.QueryIntent

/**
 * FallbackIntentClassifier coordinates between TfLiteSimilarityIntentClassifier (ML)
 * and RegexIntentClassifier (Rule-based). If ML confidence is below 0.75 or it fails,
 * it falls back to Regex.
 */
class FallbackIntentClassifier(context: Context) : IntentClassifier {
    private val TAG = "FallbackIntentClassifier"
    private val mlClassifier = TfLiteSimilarityIntentClassifier(context)
    private val regexClassifier = RegexIntentClassifier()
    private val confidenceThreshold = 0.75

    override fun classify(rawQuery: String): QueryIntent {
        // 1. Try ML similarity intent classifier
        val mlResult = mlClassifier.classify(rawQuery)
        
        if (mlResult.confidence >= confidenceThreshold) {
            Log.i(TAG, "ML classified query '$rawQuery' as ${mlResult.objective} (confidence: ${mlResult.confidence})")
            return mlResult
        }
        
        // 2. Fall back to regex if confidence is below threshold
        Log.w(TAG, "ML confidence (${mlResult.confidence}) below threshold ($confidenceThreshold). Falling back to Regex.")
        val regexResult = regexClassifier.classify(rawQuery)
        return regexResult.copy(confidence = mlResult.confidence) // Keep the original confidence for tracing
    }
}
