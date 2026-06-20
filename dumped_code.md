# Dumped Code File - ML-First Intent Classification & BYOK Architecture

This file aggregates all the core source code created and modified for the Lensly on-device MiniLM intent classification and Bring Your Own Key (BYOK) dynamic header routing.

---

## 💻 scripts/export_minilm.py
```python
import os
import sys

def main():
    # Define paths
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    assets_dir = os.path.join(base_dir, "android", "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    
    vocab_path = os.path.join(assets_dir, "vocab.txt")
    tflite_path = os.path.join(assets_dir, "minilm.tflite")

    print("--- Lensly MiniLM Export Pipeline ---")
    print(f"Target Assets Directory: {assets_dir}")

    try:
        import tensorflow as tf
        from transformers import AutoTokenizer, TFAutoModel
    except ImportError as e:
        print(f"Error importing dependencies: {e}")
        print("Please ensure you have run: pip install transformers tensorflow torch tf-keras")
        sys.exit(1)

    model_name = "sentence-transformers/all-MiniLM-L6-v2"
    
    # 1. Load and save tokenizer vocabulary
    print(f"\n[1/3] Loading tokenizer for {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    
    print(f"Saving vocabulary to: {vocab_path}")
    vocab_files = tokenizer.save_vocabulary(assets_dir)
    saved_vocab_file = vocab_files[0] if isinstance(vocab_files, (list, tuple)) else vocab_files
    if os.path.exists(saved_vocab_file) and saved_vocab_file != vocab_path:
        os.rename(saved_vocab_file, vocab_path)
    print("Vocabulary successfully saved!")

    # 2. Load and build TF model
    print(f"\n[2/3] Loading TensorFlow transformer model for {model_name}...")
    transformer = TFAutoModel.from_pretrained(model_name, from_pt=True)

    # Wrap in tf.Module to perform Mean Pooling + L2 Normalization on-device
    class MiniLMEmbedder(tf.Module):
        def __init__(self, transformer):
            super().__init__()
            self.transformer = transformer

        @tf.function(input_signature=[
            tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_ids"),
            tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="attention_mask")
        ])
        def __call__(self, input_ids, attention_mask):
            outputs = self.transformer(input_ids=input_ids, attention_mask=attention_mask)
            token_embeddings = outputs.last_hidden_state
            
            # Mean Pooling
            input_mask_expanded = tf.cast(tf.expand_dims(attention_mask, -1), tf.float32)
            sum_embeddings = tf.reduce_sum(token_embeddings * input_mask_expanded, axis=1)
            sum_mask = tf.reduce_sum(input_mask_expanded, axis=1)
            sum_mask = tf.maximum(sum_mask, 1e-9)
            embeddings = sum_embeddings / sum_mask
            
            # L2 Normalization
            normalized = tf.linalg.l2_normalize(embeddings, axis=1)
            return normalized

    embedder = MiniLMEmbedder(transformer)

    # 3. Convert to TFLite
    print("\n[3/3] Converting TensorFlow model to TFLite (with dynamic-range quantization)...")
    concrete_func = embedder.__call__.get_concrete_function(
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="input_ids"),
        tf.TensorSpec(shape=[None, None], dtype=tf.int32, name="attention_mask")
    )
    
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func], embedder)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS
    ]
    
    tflite_model = converter.convert()

    with open(tflite_path, "wb") as f:
        f.write(tflite_model)
        
    print(f"\nSuccess! Quantized TFLite model written to: {tflite_path}")
    print(f"Model File Size: {os.path.getsize(tflite_path) / (1024 * 1024):.2f} MB")
    print("Export pipeline finished successfully.")

if __name__ == "__main__":
    main()
```

---

## 💻 backend/handlers/analyze.go
```go
package handlers

import (
	"encoding/json"
	"net/http"
	"os"

	"github.com/lensly/backend/claude"
	"github.com/lensly/backend/models"
)

// AnalyzeRequest is sent by the Android app with parsed product data.
type AnalyzeRequest struct {
	Products        []models.Product        `json:"products"`
	QueryIntent     models.QueryIntent      `json:"query_intent"`
	UserPreferences models.UserPreferences  `json:"user_preferences"`
}

// Analyze handles POST /api/v1/analyze
func Analyze(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	var req AnalyzeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid request body"}`, http.StatusBadRequest)
		return
	}

	if len(req.Products) == 0 {
		http.Error(w, `{"error":"no products provided"}`, http.StatusBadRequest)
		return
	}

	needsAI := requiresAIReasoning(req.QueryIntent)

	// Extract API key from headers, fallback to environment
	apiKey := r.Header.Get("X-Anthropic-API-Key")
	if apiKey == "" {
		apiKey = os.Getenv("ANTHROPIC_API_KEY")
	}

	var resp models.AnalyzeResponse
	var err error

	if needsAI {
		resp, err = claude.Rank(req.Products, req.QueryIntent, req.UserPreferences, apiKey)
		if err != nil {
			resp = deterministicRank(req.Products, req.QueryIntent)
			resp.Explanation += " (AI unavailable — rule-based fallback)"
		}
		resp.UsedAI = true
	} else {
		resp = deterministicRank(req.Products, req.QueryIntent)
		resp.UsedAI = false
	}

	json.NewEncoder(w).Encode(resp)
}
```

---

## 💻 backend/claude/client.go
```go
package claude

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/lensly/backend/models"
)

const (
	claudeAPIURL = "https://api.anthropic.com/v1/messages"
	claudeModel  = "claude-sonnet-4-5"
	maxTokens    = 1024
	timeoutSec   = 10
)

// Rank calls Claude API to rank products based on a complex query intent using the user-provided API key.
func Rank(
	products []models.Product,
	intent models.QueryIntent,
	prefs models.UserPreferences,
	apiKey string,
) (models.AnalyzeResponse, error) {
	if apiKey == "" {
		return models.AnalyzeResponse{}, fmt.Errorf("ANTHROPIC_API_KEY not set")
	}

	prompt := buildPrompt(products, intent, prefs)

	reqBody := claudeRequest{
		Model:     claudeModel,
		MaxTokens: maxTokens,
		Messages: []message{
			{Role: "user", Content: prompt},
		},
	}

	body, err := json.Marshal(reqBody)
	if err != nil {
		return models.AnalyzeResponse{}, err
	}

	ctx, cancel := context.WithTimeout(context.Background(), timeoutSec*time.Second)
	defer cancel()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, claudeAPIURL, bytes.NewReader(body))
	if err != nil {
		return models.AnalyzeResponse{}, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-api-key", apiKey)
	req.Header.Set("anthropic-version", "2023-06-01")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return models.AnalyzeResponse{}, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return models.AnalyzeResponse{}, fmt.Errorf("claude API returned %d", resp.StatusCode)
	}

	var claudeResp claudeResponse
	if err := json.NewDecoder(resp.Body).Decode(&claudeResp); err != nil {
		return models.AnalyzeResponse{}, err
	}

	if len(claudeResp.Content) == 0 {
		return models.AnalyzeResponse{}, fmt.Errorf("empty claude response")
	}

	return parseClaudeResponse(claudeResp.Content[0].Text, products)
}
```

---

## 📱 ApiClient.kt
```kotlin
package com.example.lensly.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    var userApiKey: String? = null

    private const val BASE_URL_DEV = "http://10.0.2.2:8080/"
    private const val BASE_URL_PROD = "https://lensly-backend.fly.dev/"
    private val BASE_URL = BASE_URL_DEV

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .addHeader("X-App-Version", "1.0.0")
            
            val key = userApiKey
            if (!key.isNullOrBlank()) {
                builder.addHeader("X-Anthropic-API-Key", key)
            }
            
            chain.proceed(builder.build())
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: LenslyApiService = retrofit.create(LenslyApiService::class.java)
}
```

---

## 📱 OverlayPanel.kt (with SettingsPanel)
```kotlin
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

// (Aesthetic Color Tokens and Subcomponents omitted for brevity)

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
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E))
                    ),
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
            Text("Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color(0xFFB0B3C6))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text("Anthropic API Key", color = Color(0xFFB0B3C6), fontSize = 13.sp)
        Spacer(modifier = Modifier.height(6.dp))
        
        TextField(
            value = keyText,
            onValueChange = { keyText = it },
            placeholder = { Text("sk-ant-api03-...", color = Color(0xFFB0B3C6).copy(alpha = 0.5f), fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF16213E),
                unfocusedContainerColor = Color(0xFF16213E),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Enter your personal API key to unlock AI-based product ranking. Leave blank to use server default key.",
            color = Color(0xFFB0B3C6),
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
```

---

## 💻 scripts/generate_launcher_icons.py
```python
import os
from PIL import Image

def resize_icon(source_path, target_sizes, base_res_dir):
    if not os.path.exists(source_path):
        print(f"Source file {source_path} does not exist!")
        return

    with Image.open(source_path) as img:
        if img.mode != 'RGBA':
            img = img.convert('RGBA')

        for density, size in target_sizes.items():
            target_dir = os.path.join(base_res_dir, f"mipmap-{density}")
            os.makedirs(target_dir, exist_ok=True)

            normal_path = os.path.join(target_dir, "ic_launcher.png")
            resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
            resized_img.save(normal_path, "PNG")

            round_path = os.path.join(target_dir, "ic_launcher_round.png")
            resized_img.save(round_path, "PNG")

def main():
    source_icon = r"C:\Users\Mukul Prasad\.gemini\antigravity\brain\56cad7b0-d2f0-4120-ac9f-fd10cee395c4\launcher_icon_1780292511522.png"
    base_res_dir = r"android/app/src/main/res"

    target_sizes = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    resize_icon(source_icon, target_sizes, base_res_dir)

if __name__ == "__main__":
    main()
```
