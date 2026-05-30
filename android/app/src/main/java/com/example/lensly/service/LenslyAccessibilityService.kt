package com.example.lensly.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lensly.models.Product
import com.example.lensly.models.SourceApp
import com.example.lensly.parser.ProductParser
import com.example.lensly.screen.NodeTraverser
import com.google.gson.Gson
import java.util.concurrent.Executor

/**
 * LenslyAccessibilityService — the heart of the screen reading layer.
 */
class LenslyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "LenslyA11y"
        const val ACTION_PRODUCTS_EXTRACTED = "com.example.lensly.PRODUCTS_EXTRACTED"
        const val EXTRA_PRODUCTS_JSON = "products_json"

        // Static instance to allow the overlay service in the same process to call screenshot methods
        @Volatile
        var instance: LenslyAccessibilityService? = null
            private set

        // Minimum product cards to trigger an extraction broadcast
        private const val MIN_PRODUCTS_TO_BROADCAST = 2
    }

    private var lastExtractedPackage: String = ""
    private var lastExtractionTime: Long = 0L
    private val extractionCooldownMs = 500L  // Avoid re-extracting on every scroll

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
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
    fun extractProducts(sourceApp: SourceApp) {
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
     * Captures a screenshot using the Accessibility API (Android 11 / API 30+).
     */
    fun captureScreenshot(onBitmapCaptured: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val executor = Executor { command -> command.run() }
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val hardwareBuffer = screenshotResult.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                        val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBuffer.close()
                        onBitmapCaptured(softwareBitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Failed to capture accessibility screenshot: error code $errorCode")
                        onBitmapCaptured(null)
                    }
                }
            )
        } else {
            Log.w(TAG, "Accessibility screenshot API requires Android 11+ (API 30)")
            onBitmapCaptured(null)
        }
    }

    /**
     * Broadcasts extracted products as a local intent to the overlay service.
     */
    private fun broadcastProducts(products: List<Product>) {
        val intent = Intent(ACTION_PRODUCTS_EXTRACTED).apply {
            setPackage(packageName)
            // Serialize product list to JSON string via Gson
            putExtra(EXTRA_PRODUCTS_JSON, Gson().toJson(products))
        }
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Accessibility service unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
