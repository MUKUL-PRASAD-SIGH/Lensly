package com.example.lensly.overlay

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import com.example.lensly.ui.overlay.OverlayViewModel

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
    private var params: WindowManager.LayoutParams,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val savedStateRegistryOwner: androidx.savedstate.SavedStateRegistryOwner,
    private val viewModelStoreOwner: androidx.lifecycle.ViewModelStoreOwner,
    private val viewModel: com.example.lensly.ui.overlay.OverlayViewModel,
    private val onBubbleClick: () -> Unit
) {
    private val composeView: ComposeView = ComposeView(context)

    init {
        // Set owners BEFORE setContent so the first composition has a valid lifecycle
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        composeView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        composeView.setContent {
            FloatingBubble(
                viewModel = viewModel,
                onTap = { onBubbleClick() },
                onDrag = { dx, dy -> updatePosition(dx, dy) }
            )
        }

        windowManager.addView(composeView, params)
    }

    private fun updatePosition(dx: Float, dy: Float) {
        params.x = (params.x - dx).toInt().coerceIn(0, 200)
        params.y = (params.y + dy).toInt().coerceIn(0, 2000)
        windowManager.updateViewLayout(composeView, params)
    }

    fun remove() {
        windowManager.removeView(composeView)
    }
}

@Composable
fun FloatingBubble(
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    viewModel: OverlayViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Scale down slightly when pressed for visual feedback
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        label = "bubbleScale"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .background(
                color = Color(0xFF6C63FF),
                shape = CircleShape
            )
            // standard clickable gives us proper ripple and click handling
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap
            )
            // detectDragGestures plays perfectly with clickable (it respects touch slop)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (uiState is com.example.lensly.ui.overlay.OverlayUiState.Loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "L",
                color = Color.White,
                fontSize = 22.sp,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
