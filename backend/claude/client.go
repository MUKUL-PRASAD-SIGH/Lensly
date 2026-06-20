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

// claudeRequest is the payload sent to the Anthropic API.
type claudeRequest struct {
	Model     string    `json:"model"`
	MaxTokens int       `json:"max_tokens"`
	Messages  []message `json:"messages"`
}

type message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// claudeResponse is the raw Anthropic API response.
type claudeResponse struct {
	Content []struct {
		Text string `json:"text"`
	} `json:"content"`
}

// claudeRankResult is what we ask Claude to return as structured JSON.
type claudeRankResult struct {
	RankedProducts []struct {
		Name        string `json:"name"`
		Rank        int    `json:"rank"`
		Explanation string `json:"explanation"`
	} `json:"ranked_products"`
	Summary    string  `json:"summary"`
	Confidence float64 `json:"confidence"`
}

// Rank calls Claude API to rank products based on a complex query intent.
// Returns an AnalyzeResponse with AI-generated explanation.
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

// buildPrompt constructs the structured prompt sent to Claude.
// IMPORTANT: Never sends raw screen data. Only structured product JSON.
func buildPrompt(products []models.Product, intent models.QueryIntent, prefs models.UserPreferences) string {
	productsJSON, _ := json.MarshalIndent(products, "", "  ")
	prefsJSON, _ := json.MarshalIndent(prefs, "", "  ")

	return fmt.Sprintf(`You are a shopping intelligence assistant. Rank these products based on the user's query.

USER QUERY: %s
OBJECTIVE: %s
CATEGORY: %s

PRODUCTS:
%s

USER PREFERENCES:
%s

Respond ONLY with valid JSON in this exact format:
{
  "ranked_products": [
    {"name": "product name", "rank": 1, "explanation": "why this is best"}
  ],
  "summary": "one sentence summary of recommendation",
  "confidence": 0.9
}

Rules:
- rank 1 = best choice
- Keep explanations under 20 words
- Be specific about why (unit price, ingredients, value)
- confidence: 0.0-1.0`,
		intent.RawQuery, intent.Objective, intent.Category,
		string(productsJSON), string(prefsJSON))
}

// parseClaudeResponse parses Claude's JSON response into an AnalyzeResponse.
func parseClaudeResponse(text string, products []models.Product) (models.AnalyzeResponse, error) {
	var result claudeRankResult
	if err := json.Unmarshal([]byte(text), &result); err != nil {
		return models.AnalyzeResponse{}, fmt.Errorf("failed to parse claude response: %w", err)
	}

	// Build a lookup map from product name
	productMap := make(map[string]models.Product)
	for _, p := range products {
		productMap[p.Name] = p
	}

	ranked := make([]models.RankedProduct, 0, len(result.RankedProducts))
	for _, r := range result.RankedProducts {
		p, ok := productMap[r.Name]
		if !ok {
			continue
		}
		ranked = append(ranked, models.RankedProduct{
			Product:     p,
			Rank:        r.Rank,
			Explanation: r.Explanation,
		})
	}

	return models.AnalyzeResponse{
		RankedProducts: ranked,
		Explanation:    result.Summary,
		Confidence:     result.Confidence,
		UsedAI:         true,
	}, nil
}
