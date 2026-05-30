package com.example.lensly.parser

import com.example.lensly.models.NormalizedUnit
import com.example.lensly.models.SIUnit

/**
 * Converts raw weight/volume strings into normalized SI units.
 *
 * Supported formats:
 *   Weight: "200g", "1kg", "1000 grams", "0.5 kg", "250 Gm"
 *   Volume: "500ml", "1L", "1 litre", "250 millilitres", "0.5 liters"
 *   Unit:   "pack of 3", "3 pieces", "1 unit"
 */
object UnitNormalizer {

    private val weightRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(kg|kilogram|kilograms|g|gm|gram|grams)""",
        RegexOption.IGNORE_CASE
    )

    private val volumeRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(l|ltr|litre|litres|liter|liters|ml|millilitre|millilitres|milliliter)""",
        RegexOption.IGNORE_CASE
    )

    private val packRegex = Regex(
        """(?:pack\s+of|x|×|pieces?|units?|count|pcs?)\s*(\d+)|(\d+)\s*(?:pack\s+of|x|×|pieces?|units?|pcs?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a raw weight/volume string and returns a NormalizedUnit.
     * Returns null if the string cannot be parsed.
     */
    fun normalize(raw: String): NormalizedUnit? {
        if (raw.isBlank()) return null

        // Try weight first
        weightRegex.find(raw)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@let null
            val unitStr = match.groupValues[2].lowercase()
            val grams = when {
                unitStr.startsWith("kg") || unitStr.startsWith("kilo") -> value * 1000.0
                else -> value  // g, gm, gram, grams
            }
            return NormalizedUnit(grams, SIUnit.GRAM)
        }

        // Try volume
        volumeRegex.find(raw)?.let { match ->
            val value = match.groupValues[1].toDoubleOrNull() ?: return@let null
            val unitStr = match.groupValues[2].lowercase()
            val ml = when {
                unitStr.startsWith("l") && !unitStr.startsWith("ml") -> value * 1000.0
                else -> value  // ml, millilitre
            }
            return NormalizedUnit(ml, SIUnit.MILLILITER)
        }

        // Try pack/unit count
        packRegex.find(raw)?.let { match ->
            val countStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val count = countStr.toDoubleOrNull() ?: return@let null
            return NormalizedUnit(count, SIUnit.UNIT)
        }

        return null
    }

    /**
     * Combines a pack count with a per-item weight/volume.
     * Example: "Pack of 3" + "200g each" → 600g
     */
    fun combinePackWithUnit(packCount: NormalizedUnit, perItem: NormalizedUnit): NormalizedUnit? {
        if (packCount.unit != SIUnit.UNIT) return null
        return NormalizedUnit(packCount.value * perItem.value, perItem.unit)
    }
}
