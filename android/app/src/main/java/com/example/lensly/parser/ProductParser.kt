package com.example.lensly.parser

import com.example.lensly.models.Product
import com.example.lensly.models.SourceApp

/**
 * Extracts structured Product data from raw text collected by Accessibility tree or OCR.
 *
 * Strategy:
 *   1. Price extraction — matches ₹, Rs., INR patterns
 *   2. Weight/volume extraction — delegated to UnitNormalizer
 *   3. Discount label extraction — "X% off", "Save ₹X"
 *   4. MRP crossed-out price extraction
 */
object ProductParser {

    // ₹112, Rs.112, Rs 112, INR 112, 112.00 (if preceded by ₹ sign)
    private val priceRegex = Regex(
        """(?:₹|Rs\.?\s*|INR\s*)(\d{1,5}(?:[.,]\d{1,2})?)"""
    )

    // Crossed-out / MRP price — appears near "MRP" label or has strikethrough
    private val mrpRegex = Regex(
        """(?:MRP|M\.R\.P\.?)\s*:?\s*(?:₹|Rs\.?\s*)(\d{1,5}(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    // "52% off", "Save 20%"
    private val discountRegex = Regex(
        """(\d{1,3})\s*%\s*(?:off|discount|savings?)|save\s*(?:₹|Rs\.?\s*)?\d+""",
        RegexOption.IGNORE_CASE
    )

    // Weight & volume strings like "200g", "1kg", "500ml", "1L"
    private val weightVolumeHintRegex = Regex(
        """(\d+(?:\.\d+)?)\s*(?:kg|g|gm|gram|grams|l|ltr|litre|litres|ml|millilitre)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a single block of product text into a [Product].
     *
     * @param rawText  Combined text content from one product card
     * @param sourceApp  Which shopping app this came from
     */
    fun parse(rawText: String, sourceApp: SourceApp = SourceApp.UNKNOWN): Product? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Name: first non-price, non-weight line (usually the longest descriptive line)
        val name = lines.firstOrNull { line ->
            !priceRegex.containsMatchIn(line) && !weightVolumeHintRegex.containsMatchIn(line)
        } ?: lines.first()

        // Price: lowest numeric price found (actual selling price, not MRP)
        val allPrices = priceRegex.findAll(rawText)
            .mapNotNull { it.groupValues[1].replace(",", ".").toDoubleOrNull() }
            .sorted()
            .toList()

        val price = allPrices.firstOrNull() ?: return null

        // MRP / crossed-out price
        val mrp = mrpRegex.find(rawText)?.groupValues?.get(1)
            ?.replace(",", ".")?.toDoubleOrNull()
            ?: allPrices.lastOrNull()?.takeIf { it > price }

        // Weight / volume
        val weightVolumeRaw = weightVolumeHintRegex.find(rawText)?.value
        val normalizedUnit = weightVolumeRaw?.let { UnitNormalizer.normalize(it) }

        // Discount label
        val discountLabel = discountRegex.find(rawText)?.value

        return Product(
            name = name.take(100),
            priceInr = price,
            weight = normalizedUnit?.takeIf {
                it.unit == com.example.lensly.models.SIUnit.GRAM
            },
            volume = normalizedUnit?.takeIf {
                it.unit == com.example.lensly.models.SIUnit.MILLILITER
            },
            mrpCrossedOut = mrp,
            discountLabel = discountLabel,
            sourceApp = sourceApp,
            rawWeightString = weightVolumeRaw
        )
    }

    /**
     * Parses a list of raw product text blocks (e.g., one per card on screen).
     * Filters out nulls and duplicates by name.
     */
    fun parseAll(rawBlocks: List<String>, sourceApp: SourceApp): List<Product> {
        return rawBlocks
            .mapNotNull { parse(it, sourceApp) }
            .distinctBy { it.name.lowercase().take(30) }
    }
}
