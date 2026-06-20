package com.example.lensly.ui.overlay

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lensly.models.RankedProduct

// ---------------------------------------------------------------------------
// Lensly Design Tokens
// ---------------------------------------------------------------------------
private val Purple = Color(0xFF6C63FF)
private val PurpleLight = Color(0xFF9C94FF)
private val PurpleDark = Color(0xFF3D35CC)
private val Surface = Color(0xFF1A1A2E)
private val SurfaceCard = Color(0xFF16213E)
private val SurfaceCardBorder = Color(0xFF0F3460)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFB0B3C6)
private val GreenAccent = Color(0xFF00D4A3)
private val OrangeAccent = Color(0xFFFF6B35)
private val FakeDiscountRed = Color(0xFFFF4757)

// ---------------------------------------------------------------------------
// Overlay Panel — Main Composable
// ---------------------------------------------------------------------------

@Composable
fun OverlayPanel(
    uiState: OverlayUiState,
    recentQueries: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onWhyTap: (RankedProduct) -> Unit,
    onBack: () -> Unit,
    onQuerySubmit: (String) -> Unit,
    onVoiceSearchTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettings by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = uiState !is OverlayUiState.Idle,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .width(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Surface, SurfaceCard)
                    ),
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                )
                .border(
                    width = 1.dp,
                    color = SurfaceCardBorder,
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                )
        ) {
            if (showSettings) {
                SettingsPanel(
                    onClose = { showSettings = false },
                    context = androidx.compose.ui.platform.LocalContext.current
                )
            } else {
                when (uiState) {
                    is OverlayUiState.Loading -> LoadingPanel()
                    is OverlayUiState.Results -> ResultsPanel(
                        state = uiState,
                        recentQueries = recentQueries,
                        onDismiss = onDismiss,
                        onWhyTap = onWhyTap,
                        onQuerySubmit = onQuerySubmit,
                        onVoiceSearchTap = onVoiceSearchTap,
                        onSettingsTap = { showSettings = true }
                    )
                is OverlayUiState.Explanation -> ExplanationPanel(
                    state = uiState,
                    onBack = onBack,
                    onDismiss = onDismiss
                )
                is OverlayUiState.Error -> ErrorPanel(
                    message = uiState.message,
                    onDismiss = onDismiss
                )
                else -> {}
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading State
// ---------------------------------------------------------------------------

@Composable
private fun LoadingPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Purple, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Analyzing products...", color = TextSecondary, fontSize = 14.sp)
    }
}

// ---------------------------------------------------------------------------
// Results Panel
// ---------------------------------------------------------------------------

@Composable
private fun ResultsPanel(
    state: OverlayUiState.Results,
    recentQueries: List<String>,
    onDismiss: () -> Unit,
    onWhyTap: (RankedProduct) -> Unit,
    onQuerySubmit: (String) -> Unit,
    onVoiceSearchTap: () -> Unit,
    onSettingsTap: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        PanelHeader(
            title = "Best Value",
            subtitle = if (state.usedAi) "AI-enhanced ranking" else "Value ranking",
            onSettingsTap = onSettingsTap,
            onDismiss = onDismiss
        )

        // Summary explanation
        if (state.explanation.isNotBlank()) {
            Text(
                text = state.explanation,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))

        // Product cards list
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.ranked) { ranked ->
                ProductRankCard(ranked = ranked, onWhyTap = onWhyTap)
            }
        }

        HorizontalDivider(color = SurfaceCardBorder, modifier = Modifier.padding(vertical = 4.dp))

        // Query input section at the bottom
        QueryInputSection(
            recentQueries = recentQueries,
            onSubmit = onQuerySubmit,
            onVoiceTap = onVoiceSearchTap
        )
    }
}

// ---------------------------------------------------------------------------
// Query Input Section
// ---------------------------------------------------------------------------

@Composable
private fun QueryInputSection(
    recentQueries: List<String>,
    onSubmit: (String) -> Unit,
    onVoiceTap: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
    ) {
        // Quick suggestion chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 6.dp)
        ) {
            val defaultSuggestions = listOf("Best Value", "Healthy", "Cheapest", "High Protein")
            val suggestions = if (recentQueries.isNotEmpty()) recentQueries else defaultSuggestions
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onSubmit(suggestion) },
                    label = { Text(suggestion, fontSize = 10.sp, color = TextPrimary) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SurfaceCard
                    ),
                    border = BorderStroke(0.5.dp, SurfaceCardBorder)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ask Lensly...", fontSize = 11.sp, color = TextSecondary) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onVoiceTap, modifier = Modifier.size(24.dp)) {
                        Text("🎙", fontSize = 14.sp)
                    }
                }
            )
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSubmit(text)
                        text = ""
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(Purple, CircleShape)
            ) {
                Text("➔", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Product Rank Card
// ---------------------------------------------------------------------------

@Composable
private fun ProductRankCard(
    ranked: RankedProduct,
    onWhyTap: (RankedProduct) -> Unit
) {
    val rankColor = when (ranked.rank) {
        1 -> GreenAccent
        2 -> PurpleLight
        else -> TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            width = if (ranked.rank == 1) 1.5.dp else 0.5.dp,
            color = if (ranked.rank == 1) GreenAccent.copy(alpha = 0.5f) else SurfaceCardBorder
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Rank badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(rankColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.dp, rankColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${ranked.rank}",
                        color = rankColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Product name
                Text(
                    text = ranked.product.name,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price + metric row
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "₹${ranked.product.priceInr.toInt()}",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format("%.3f %s", ranked.scorePerUnit, ranked.metric),
                        color = rankColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Fake discount warning badge
                if (ranked.isFakeDiscount) {
                    Surface(
                        color = FakeDiscountRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, FakeDiscountRed)
                    ) {
                        Text(
                            text = "⚠ Fake Discount",
                            color = FakeDiscountRed,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Explanation + Why? button
            if (ranked.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ranked.explanation,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onWhyTap(ranked) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Why?",
                            tint = Purple,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Why?", color = Purple, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Explanation Panel
// ---------------------------------------------------------------------------

@Composable
private fun ExplanationPanel(
    state: OverlayUiState.Explanation,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PanelHeader(title = "Why #${state.product.rank}?", onBack = onBack, onDismiss = onDismiss)
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = state.product.product.name,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Metric breakdown
        ExplanationRow("Price", "₹${state.product.product.priceInr.toInt()}")
        state.product.product.weight?.let {
            ExplanationRow("Weight", "${it.value.toInt()}${it.unit.name.lowercase()}")
        }
        state.product.product.volume?.let {
            ExplanationRow("Volume", "${it.value.toInt()}${it.unit.name.lowercase()}")
        }
        ExplanationRow(
            state.product.metric,
            String.format("%.4f", state.product.scorePerUnit)
        )
        state.product.product.mrpCrossedOut?.let {
            ExplanationRow("MRP", "₹${it.toInt()}")
        }
        if (state.product.isFakeDiscount) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = FakeDiscountRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, FakeDiscountRed)
            ) {
                Text(
                    text = "⚠ ${state.product.fakeDiscountReason ?: "Discount appears misleading"}",
                    color = FakeDiscountRed,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = state.product.explanation,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ExplanationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.5f))
}

// ---------------------------------------------------------------------------
// Error Panel
// ---------------------------------------------------------------------------

@Composable
private fun ErrorPanel(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("😕", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = TextSecondary, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Purple)
        ) { Text("Dismiss") }
    }
}

// ---------------------------------------------------------------------------
// Panel Header
// ---------------------------------------------------------------------------

@Composable
private fun PanelHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onSettingsTap: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(PurpleDark, Purple)),
                RoundedCornerShape(topStart = 20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("←", color = Color.White, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            // Logo mark
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
        if (onSettingsTap != null) {
            IconButton(onClick = onSettingsTap, modifier = Modifier.size(28.dp)) {
                Text("⚙", color = Color.White, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

// ---------------------------------------------------------------------------
// Settings Panel (BYOK Key Configuration)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsPanel(
    onClose: () -> Unit,
    context: android.content.Context
) {
    val prefs = remember { context.getSharedPreferences("lensly_prefs", android.content.Context.MODE_PRIVATE) }
    var keyText by remember { mutableStateOf(prefs.getString("anthropic_api_key", "") ?: "") }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = TextSecondary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Anthropic API Key", color = TextSecondary, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        
        TextField(
            value = keyText,
            onValueChange = { keyText = it },
            placeholder = { Text("sk-ant-api03-...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Enter your personal API key to unlock AI-based product ranking. Leave blank to use server default key.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                prefs.edit().putString("anthropic_api_key", keyText.trim()).apply()
                com.example.lensly.network.ApiClient.userApiKey = keyText.trim().ifEmpty { null }
                onClose()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
