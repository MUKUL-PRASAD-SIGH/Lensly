package com.example.lensly.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import com.example.lensly.R

/**
 * OverlayService — persistent foreground service that manages the overlay UI.
 *
 * Responsibilities:
 *   - Attaches the floating bubble button to the WindowManager
 *   - Controls the slide-in side panel visibility
 *   - Runs as a foreground service to survive app switching
 *
 * The overlay is a ComposeView attached with TYPE_APPLICATION_OVERLAY,
 * which requires SYSTEM_ALERT_WINDOW permission granted by the user.
 */
class OverlayService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lensly_overlay"
        const val ACTION_SHOW = "com.example.lensly.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.lensly.ACTION_HIDE"
    }

    private lateinit var windowManager: WindowManager
    private var floatingButtonView: FloatingButtonView? = null
    private var isShowing = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingButton()
            ACTION_HIDE -> hideFloatingButton()
        }
        return START_STICKY
    }

    private fun showFloatingButton() {
        if (isShowing || floatingButtonView != null) return

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

        floatingButtonView = FloatingButtonView(this, windowManager, params)
        isShowing = true
    }

    private fun hideFloatingButton() {
        floatingButtonView?.let {
            it.remove()
            floatingButtonView = null
        }
        isShowing = false
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideFloatingButton()
        super.onDestroy()
    }
}
