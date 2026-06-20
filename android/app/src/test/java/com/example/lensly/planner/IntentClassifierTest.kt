package com.example.lensly.planner

import com.example.lensly.models.Objective
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class IntentClassifierTest {
    
    private lateinit var regexClassifier: RegexIntentClassifier
    
    @Before
    fun setup() {
        regexClassifier = RegexIntentClassifier()
    }

    @Test
    fun testHealthQueryIntent() {
        val result = regexClassifier.classify("show me the most healthy protein powder")
        assertEquals(Objective.MAXIMIZE_HEALTH_SCORE, result.objective)
        assertEquals("powder", result.category)
        assertEquals("show me the most healthy protein powder", result.rawQuery)
    }

    @Test
    fun testCheapQueryIntent() {
        val result = regexClassifier.classify("find cheapest milk")
        assertEquals(Objective.MINIMIZE_PRICE_PER_UNIT, result.objective)
        assertEquals("milk", result.category)
    }

    @Test
    fun testBudgetQueryIntent() {
        val result = regexClassifier.classify("snacks under 150 rupees")
        assertEquals(Objective.MINIMIZE_PRICE_PER_UNIT, result.objective)
        assertEquals(150.0, result.maxPriceInr)
    }

    @Test
    fun testDiscountQueryIntent() {
        val result = regexClassifier.classify("best offers and deals on soap")
        assertEquals(Objective.DETECT_FAKE_DISCOUNT, result.objective)
        assertEquals("soap", result.category)
    }

    @Test
    fun testCompareQueryIntent() {
        val result = regexClassifier.classify("compare dove and pepsodent")
        assertEquals(Objective.BEST_OVERALL, result.objective)
    }

    @Test
    fun testDefaultQueryIntent() {
        val result = regexClassifier.classify("just general recommendations")
        assertEquals(Objective.BEST_VALUE, result.objective)
    }
}
