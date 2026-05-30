package com.example.lensly

import com.example.lensly.engine.ValueEngine
import com.example.lensly.models.*
import com.example.lensly.parser.UnitNormalizer
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UnitNormalizer — pure parsing logic, no Android deps.
 */
class UnitNormalizerTest {

    @Test fun `parse grams`() {
        val result = UnitNormalizer.normalize("200g")
        assertEquals(200.0, result!!.value, 0.001)
        assertEquals(SIUnit.GRAM, result.unit)
    }

    @Test fun `parse kilograms`() {
        val result = UnitNormalizer.normalize("1kg")
        assertEquals(1000.0, result!!.value, 0.001)
        assertEquals(SIUnit.GRAM, result.unit)
    }

    @Test fun `parse millilitres`() {
        val result = UnitNormalizer.normalize("500ml")
        assertEquals(500.0, result!!.value, 0.001)
        assertEquals(SIUnit.MILLILITER, result.unit)
    }

    @Test fun `parse litres`() {
        val result = UnitNormalizer.normalize("1L")
        assertEquals(1000.0, result!!.value, 0.001)
        assertEquals(SIUnit.MILLILITER, result.unit)
    }

    @Test fun `parse with spaces`() {
        val result = UnitNormalizer.normalize("1 kg")
        assertEquals(1000.0, result!!.value, 0.001)
    }

    @Test fun `parse grams with word`() {
        val result = UnitNormalizer.normalize("250 grams")
        assertEquals(250.0, result!!.value, 0.001)
    }

    @Test fun `returns null for unrecognized`() {
        assertNull(UnitNormalizer.normalize("pack"))
        assertNull(UnitNormalizer.normalize(""))
        assertNull(UnitNormalizer.normalize("   "))
    }

    @Test fun `parse decimal kg`() {
        val result = UnitNormalizer.normalize("0.5kg")
        assertEquals(500.0, result!!.value, 0.001)
    }
}

/**
 * Unit tests for ValueEngine — pure math, no Android deps.
 */
class ValueEngineTest {

    private fun product(
        name: String,
        price: Double,
        weightG: Double? = null,
        volumeMl: Double? = null,
        mrp: Double? = null
    ) = Product(
        name = name,
        priceInr = price,
        weight = weightG?.let { NormalizedUnit(it, SIUnit.GRAM) },
        volume = volumeMl?.let { NormalizedUnit(it, SIUnit.MILLILITER) },
        mrpCrossedOut = mrp
    )

    @Test fun `rankByValue sorts ascending by price per gram`() {
        val products = listOf(
            product("A", 100.0, weightG = 200.0),   // 0.5 ₹/g
            product("B", 80.0, weightG = 100.0),    // 0.8 ₹/g
            product("C", 120.0, weightG = 300.0)    // 0.4 ₹/g — BEST
        )
        val ranked = ValueEngine.rankByValue(products)
        assertEquals("C", ranked[0].product.name)  // 0.4 ₹/g
        assertEquals("A", ranked[1].product.name)  // 0.5 ₹/g
        assertEquals("B", ranked[2].product.name)  // 0.8 ₹/g
    }

    @Test fun `computeUnitPrice uses weight when available`() {
        val p = product("Test", 100.0, weightG = 200.0)
        val (price, metric) = ValueEngine.computeUnitPrice(p)!!
        assertEquals(0.5, price, 0.001)
        assertEquals("₹/gram", metric)
    }

    @Test fun `computeUnitPrice uses volume when no weight`() {
        val p = product("Test", 100.0, volumeMl = 500.0)
        val (price, metric) = ValueEngine.computeUnitPrice(p)!!
        assertEquals(0.2, price, 0.001)
        assertEquals("₹/ml", metric)
    }

    @Test fun `computeUnitPrice returns null with no unit`() {
        val p = product("Test", 100.0)
        assertNull(ValueEngine.computeUnitPrice(p))
    }

    @Test fun `detectFakeDiscount flags impossible MRP`() {
        // MRP lower than selling price
        val p = product("Test", 100.0, mrp = 90.0)
        val verdict = ValueEngine.detectFakeDiscount(p)
        assertTrue(verdict.isLikelyFake)
    }

    @Test fun `detectFakeDiscount flags inflated MRP (over 70% off)`() {
        val p = product("Test", 50.0, mrp = 200.0)  // 75% off
        val verdict = ValueEngine.detectFakeDiscount(p)
        assertTrue(verdict.isLikelyFake)
        assertNotNull(verdict.reason)
    }

    @Test fun `detectFakeDiscount passes legitimate discount`() {
        val p = product("Test", 80.0, mrp = 100.0)  // 20% off — normal
        val verdict = ValueEngine.detectFakeDiscount(p)
        assertFalse(verdict.isLikelyFake)
    }

    @Test fun `rank assigns correct rank numbers`() {
        val products = listOf(
            product("A", 100.0, weightG = 200.0),
            product("B", 50.0, weightG = 200.0)
        )
        val ranked = ValueEngine.rankByValue(products)
        assertEquals(1, ranked[0].rank)
        assertEquals(2, ranked[1].rank)
    }
}
