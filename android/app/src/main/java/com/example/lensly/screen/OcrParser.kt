package com.example.lensly.screen

import android.graphics.Bitmap
import android.util.Log
import com.example.lensly.models.Product
import com.example.lensly.models.SourceApp
import com.example.lensly.parser.ProductParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.isActive

/**
 * OcrParser — uses Google ML Kit Text Recognition to detect products from screenshots.
 * Used as a fallback when the Accessibility node tree returns empty content.
 */
object OcrParser {
    private const val TAG = "LenslyOCR"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Runs OCR on a bitmap, parses detected text into products, and returns them.
     */
    suspend fun parseFromBitmap(bitmap: Bitmap, sourceApp: SourceApp = SourceApp.UNKNOWN): List<Product> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val products = processVisionText(visionText.text, sourceApp)
                    Log.d(TAG, "OCR successfully detected ${products.size} products")
                    if (continuation.isActive) {
                        continuation.resume(products)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR text recognition failed", e)
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }

    /**
     * Processes raw recognized text and splits it into product-like text blocks.
     * Heuristics:
     * - We split by double newlines or group lines that appear to form a card.
     * - In OCR output, lines from the same card often appear sequentially.
     * - We group lines together until we see a price or "Add" or an empty line,
     *   which usually signals the end of a card block.
     */
    private fun processVisionText(text: String, sourceApp: SourceApp): List<Product> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            currentBlock.append(line).append("\n")
            // Heuristic boundary: a line indicating addition to cart or a price followed by new item info.
            // When we see "ADD", "Add to cart", or a pattern containing "ADD" button, we split the card.
            if (line.equals("ADD", ignoreCase = true) || 
                line.contains("Add to cart", ignoreCase = true) ||
                line.contains("Out of stock", ignoreCase = true)
            ) {
                blocks.add(currentBlock.toString())
                currentBlock = StringBuilder()
            }
        }

        // Add final block if not empty
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString())
        }

        // If no clean "ADD" button boundaries were found, fallback to block chunks of 3 lines
        if (blocks.size <= 1 && lines.size > 3) {
            blocks.clear()
            for (i in lines.indices step 3) {
                val end = minOf(i + 3, lines.size)
                val blockText = lines.subList(i, end).joinToString("\n")
                blocks.add(blockText)
            }
        }

        return ProductParser.parseAll(blocks, sourceApp)
    }
}
