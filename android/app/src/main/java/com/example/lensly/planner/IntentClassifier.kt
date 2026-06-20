package com.example.lensly.planner

import com.example.lensly.models.QueryIntent

/**
 * Interface for parsing a user's natural language query into a structured Intent.
 */
interface IntentClassifier {
    fun classify(rawQuery: String): QueryIntent
}
