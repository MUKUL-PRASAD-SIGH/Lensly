package com.example.lensly.overlay

import android.content.Context
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * FloatingButtonView — the draggable bubble that triggers the overlay panel.
 *
 * Features:
 *   - Attached to WindowManager as TYPE_APPLICATION_OVERLAY
 *   - Draggable: user can move it anywhere on screen, position remembered
 *   - Tap: opens the overlay analysis panel
 *   - Long press: quick settings
 */
class FloatingButtonView(
    private val context: Context,
    private val windowManager: WindowManager,
    private var params: WindowManager.LayoutParams
) {
    private val composeView: ComposeView = ComposeView(context).apply {
        setContent {
            FloatingBubble(
                onTap = { openPanel() },
                onDrag = { dx, dy -> updatePosition(dx, dy) }
            )
        }
    }

    init {
        windowManager.addView(composeView, params)
    }

    private fun updatePosition(dx: Float, dy: Float) {
        params.x = (params.x - dx).toInt().coerceIn(0, 200)
        params.y = (params.y + dy).toInt().coerceIn(0, 2000)
        windowManager.updateViewLayout(composeView, params)
    }

    private fun openPanel() {
        // TODO Phase 3: Show overlay analysis panel
    }

    fun remove() {
        windowManager.removeView(composeView)
    }
}

@Composable
fun FloatingBubble(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                color = Color(0xFF6C63FF),
                shape = CircleShape
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDrag = { _, dragAmount ->
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "L",
            color = Color.White,
            fontSize = 22.sp,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
