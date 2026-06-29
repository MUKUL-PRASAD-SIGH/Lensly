package com.example.lensly.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import com.example.lensly.models.Product
import com.example.lensly.models.SourceApp
import com.example.lensly.screen.OcrParser
import com.example.lensly.service.LenslyAccessibilityService
import com.example.lensly.ui.overlay.OverlayPanel
import com.example.lensly.ui.overlay.OverlayViewModel
import com.example.lensly.ui.overlay.OverlayUiState
import com.example.lensly.db.AppDatabase
import com.example.lensly.planner.IntentClassifier
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OverlayService — persistent foreground service that manages the overlay UI.
 * Implements LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
 * to support Jetpack Compose and ViewModels directly inside the Service window.
 */
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "LenslyOverlay"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lensly_overlay"
        const val ACTION_SHOW = "com.example.lensly.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.lensly.ACTION_HIDE"
    }

    private lateinit var windowManager: WindowManager
    private var floatingButtonView: FloatingButtonView? = null
    private var panelView: ComposeView? = null
    private var isFloatingButtonShowing = false
    private var isPanelShowing = false

    // Lifecycle, VM store, and Saved State Registry manual implementations
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    // Service coroutine scope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy initialization of OverlayViewModel
    private val viewModel by lazy {
        val dao = AppDatabase.getDatabase(this).queryHistoryDao()
        val classifier: com.example.lensly.planner.IntentClassifier = com.example.lensly.planner.FallbackIntentClassifier(this)
        ViewModelProvider(this, OverlayViewModel.Factory(dao, classifier))[OverlayViewModel::class.java]
    }

    // Stores the latest products detected on screen via accessibility service
    private var latestProducts: List<Product> = emptyList()

    // Listens for product extraction broadcasts from the accessibility service
    private val productsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LenslyAccessibilityService.ACTION_PRODUCTS_EXTRACTED) {
                val json = intent.getStringExtra(LenslyAccessibilityService.EXTRA_PRODUCTS_JSON)
                if (!json.isNullOrBlank()) {
                    try {
                        val products: List<Product> = Gson().fromJson(
                            json,
                            object : TypeToken<List<Product>>() {}.type
                        )
                        latestProducts = products
                        Log.d(TAG, "Received ${products.size} products from accessibility broadcast")
                        
                        // Auto-refresh the panel if it is currently displaying results
                        if (isPanelShowing && viewModel.uiState.value is OverlayUiState.Results) {
                            viewModel.analyze(products, "best value")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse products JSON: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onCreate() {
        // Load API Key from preferences
        val prefs = getSharedPreferences("lensly_prefs", MODE_PRIVATE)
        com.example.lensly.network.ApiClient.userApiKey = prefs.getString("anthropic_api_key", null)

        savedStateController.performRestore(null)
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        showFloatingButton()

        // Register the broadcast receiver
        val filter = IntentFilter(LenslyAccessibilityService.ACTION_PRODUCTS_EXTRACTED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(productsReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(productsReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SHOW -> showFloatingButton()
            ACTION_HIDE -> {
                hidePanel()
                hideFloatingButton()
            }
        }
        return START_STICKY
    }

    private fun showFloatingButton() {
        if (isFloatingButtonShowing || floatingButtonView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 300
        }

        floatingButtonView = FloatingButtonView(
            context = this,
            windowManager = windowManager,
            params = params,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            viewModelStoreOwner = this,
            viewModel = viewModel
        ) {
            togglePanel()
        }
        isFloatingButtonShowing = true
    }

    private fun hideFloatingButton() {
        floatingButtonView?.let {
            it.remove()
            floatingButtonView = null
        }
        isFloatingButtonShowing = false
    }

    private fun togglePanel() {
        if (isPanelShowing) {
            hidePanel()
        } else {
            showPanel()
            triggerAnalysis()
        }
    }

    private fun showPanel() {
        if (panelView != null) return

        val params = WindowManager.LayoutParams(
            dpToPx(300),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END
        }

        panelView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            
            setContent {
                val uiState by viewModel.uiState.collectAsState()
                val recentQueries by viewModel.recentQueries.collectAsState()
                OverlayPanel(
                    uiState = uiState,
                    recentQueries = recentQueries,
                    onDismiss = { hidePanel() },
                    onWhyTap = { viewModel.showExplanation(it) },
                    onBack = { viewModel.backToResults() },
                    onQuerySubmit = { query -> triggerAnalysis(query) },
                    onVoiceSearchTap = { startVoiceInput() }
                )
            }
        }

        windowManager.addView(panelView, params)
        isPanelShowing = true
    }

    private fun hidePanel() {
        panelView?.let {
            windowManager.removeView(it)
            panelView = null
        }
        isPanelShowing = false
        viewModel.dismiss()
    }

    /**
     * Triggers the analysis flow.
     * Uses captured accessibility products if available.
     * Otherwise, falls back to taking a screenshot and running on-device OCR.
     */
    private fun triggerAnalysis(query: String = "best value") {
        viewModel.startLoading()

        if (latestProducts.isNotEmpty()) {
            Log.d(TAG, "Analyzing existing accessibility products list")
            viewModel.analyze(latestProducts, query)
        } else {
            Log.d(TAG, "Accessibility list is empty, falling back to OCR screenshot")
            val service = LenslyAccessibilityService.instance
            if (service != null) {
                // IMPORTANT: Hide panel before screenshot so it doesn't obstruct the app
                panelView?.visibility = android.view.View.INVISIBLE
                // Small delay to let the panel disappear from the frame buffer
                serviceScope.launch {
                    kotlinx.coroutines.delay(150)
                    service.captureScreenshot { bitmap ->
                        // Make panel visible again immediately
                        panelView?.visibility = android.view.View.VISIBLE
                        if (bitmap != null) {
                            serviceScope.launch {
                                try {
                                    val products = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        OcrParser.parseFromBitmap(bitmap)
                                    }
                                    if (products.isNotEmpty()) {
                                        latestProducts = products
                                        viewModel.analyze(products, query)
                                    } else {
                                        viewModel.showError("No products detected on screen via OCR")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "OCR crashed", e)
                                    viewModel.showError("OCR Processing Failed: ${e.message}")
                                }
                            }
                        } else {
                            viewModel.showError("Failed to capture screenshot. Make sure Accessibility is enabled.")
                        }
                    }
                }
            } else {
                viewModel.showError("Accessibility Service is not running. Please enable it.")
            }
        }
    }

    /**
     * Starts voice recognition using SpeechRecognizer.
     */
    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak query (e.g., 'sugar-free options')")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@OverlayService, "Listening...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Grant microphone permission in Settings"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Voice recognition failed"
                }
                Toast.makeText(this@OverlayService, message, Toast.LENGTH_SHORT).show()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val query = matches?.firstOrNull()
                if (!query.isNullOrBlank()) {
                    triggerAnalysis(query)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lensly Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the Lensly overlay active while shopping"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Lensly is active")
            .setContentText("Tap the floating button while shopping to compare products")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        unregisterReceiver(productsReceiver)
        hidePanel()
        hideFloatingButton()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
