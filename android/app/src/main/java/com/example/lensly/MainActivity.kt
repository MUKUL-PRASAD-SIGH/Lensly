package com.example.lensly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lensly.overlay.OverlayService
import com.example.lensly.theme.LenslyTheme

/**
 * MainActivity — onboarding + permission setup screen.
 *
 * Responsibilities:
 *   1. Check if SYSTEM_ALERT_WINDOW permission is granted
 *   2. Check if Accessibility Service is enabled
 *   3. Guide user to enable both
 *   4. Start OverlayService once permissions are ready
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LenslyTheme {
                OnboardingScreen(
                    hasOverlayPermission = Settings.canDrawOverlays(this),
                    hasAccessibilityEnabled = isAccessibilityEnabled(),
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onStartApp = { startOverlayService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission state when user returns from settings
        if (Settings.canDrawOverlays(this) && isAccessibilityEnabled()) {
            startOverlayService()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "$packageName/com.example.lensly.service.LenslyAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
            .apply { action = OverlayService.ACTION_SHOW }
        startForegroundService(intent)
    }
}

// ---------------------------------------------------------------------------
// Onboarding Screen UI
// ---------------------------------------------------------------------------

private val Purple = Color(0xFF6C63FF)
private val PurpleDark = Color(0xFF3D35CC)
private val Surface = Color(0xFF1A1A2E)
private val SurfaceCard = Color(0xFF16213E)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB0B3C6)
private val GreenAccent = Color(0xFF00D4A3)

@Composable
fun OnboardingScreen(
    hasOverlayPermission: Boolean,
    hasAccessibilityEnabled: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onStartApp: () -> Unit
) {
    val allGranted = hasOverlayPermission && hasAccessibilityEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Surface, SurfaceCard)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(listOf(Purple, PurpleDark)),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Lensly",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Smart Shopping Intelligence",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // Permission cards
            PermissionCard(
                emoji = "🪟",
                title = "Overlay Permission",
                description = "Shows the floating button on top of shopping apps",
                isGranted = hasOverlayPermission,
                onRequest = onRequestOverlay
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                emoji = "♿",
                title = "Accessibility Service",
                description = "Reads product listings to find the best value for you",
                isGranted = hasAccessibilityEnabled,
                onRequest = onRequestAccessibility
            )

            Spacer(Modifier.height(32.dp))

            if (allGranted) {
                Button(
                    onClick = onStartApp,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple)
                ) {
                    Text("Start Shopping Smarter →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "Grant both permissions above to activate Lensly",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    emoji: String,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextSecondary, fontSize = 12.sp)
            }
            if (isGranted) {
                Text("✓", color = GreenAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                TextButton(onClick = onRequest) {
                    Text("Enable", color = Purple, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
