package com.example.lensly.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lensly.models.Product
import com.example.lensly.models.SourceApp
import com.example.lensly.parser.ProductParser
import com.example.lensly.screen.NodeTraverser

/**
 * LenslyAccessibilityService — the heart of the screen reading layer.
 *
 * Lifecycle:
 *   - Enabled by user in Android Settings → Accessibility
 *   - Receives events when foreground app content changes
 *   - Traverses the UI tree to extract product card data
 *   - Broadcasts extracted products to the overlay layer
 *
 * Privacy:
 *   - Never stores raw screen data
 *   - Only structured product fields (name, price, weight) are extracted
 *   - No data is persisted beyond the current query session
 */
class LenslyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LenslyA11y"
        const val ACTION_PRODUCTS_EXTRACTED = "com.example.lensly.PRODUCTS_EXTRACTED"
        const val EXTRA_PRODUCTS_JSON = "products_json"

        // Minimum product cards to trigger an extraction broadcast
        private const val MIN_PRODUCTS_TO_BROADCAST = 2
    }

    private var lastExtractedPackage: String = ""
    private var lastExtractionTime: Long = 0L
    private val extractionCooldownMs = 500L  // Avoid re-extracting on every scroll

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ Lensly Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Only care about content changes and window state changes
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // Only process supported shopping apps
        val sourceApp = SourceApp.values().find { it.packageName == packageName }
            ?: return

        // Debounce: skip if same package extracted recently
        val now = System.currentTimeMillis()
        if (packageName == lastExtractedPackage && now - lastExtractionTime < extractionCooldownMs) {
            return
        }

        extractProducts(sourceApp)
    }

    /**
     * Traverses the active window's node tree and extracts visible product cards.
     */
    private fun extractProducts(sourceApp: SourceApp) {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "Root window unavailable for ${sourceApp.name}")
            return
        }

        try {
            val rawCardTexts = NodeTraverser.extractProductCardTexts(root)
            if (rawCardTexts.size < MIN_PRODUCTS_TO_BROADCAST) return

            val products = ProductParser.parseAll(rawCardTexts, sourceApp)
            if (products.isEmpty()) return

            lastExtractedPackage = sourceApp.packageName
            lastExtractionTime = System.currentTimeMillis()

            Log.d(TAG, "Extracted ${products.size} products from ${sourceApp.name}")
            broadcastProducts(products)
        } finally {
            root.recycle()
        }
    }

    /**
     * Broadcasts extracted products as a local intent to the overlay service.
     */
    private fun broadcastProducts(products: List<Product>) {
        val intent = Intent(ACTION_PRODUCTS_EXTRACTED).apply {
            setPackage(packageName)
            // Serialize product list to JSON string for IPC
            // In a real build, use kotlinx.serialization or Gson
            putExtra(EXTRA_PRODUCTS_JSON, products.toString())
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }
}
