package handlers

import (
	"encoding/json"
	"net/http"

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
// Receives structured product JSON from the Android app and returns ranked results.
// Invokes Claude API only when the query requires reasoning beyond deterministic math.
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

	// Route: simple value queries use deterministic ranking; complex queries use Claude.
	needsAI := requiresAIReasoning(req.QueryIntent)

	var resp models.AnalyzeResponse
	var err error


	if needsAI {
		resp, err = claude.Rank(req.Products, req.QueryIntent, req.UserPreferences)
		if err != nil {
			// Graceful degradation: fall back to rule-based ranking
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

// Rank handles POST /api/v1/rank — always uses deterministic ranking (no AI).
func Rank(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")

	var req AnalyzeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"error":"invalid request body"}`, http.StatusBadRequest)
		return
	}

	resp := deterministicRank(req.Products, req.QueryIntent)
	json.NewEncoder(w).Encode(resp)
}

// requiresAIReasoning returns true for objectives that cannot be resolved by math alone.
func requiresAIReasoning(intent models.QueryIntent) bool {
	aiObjectives := map[string]bool{
		"MAXIMIZE_HEALTH_SCORE":  true,
		"BEST_OVERALL":           true,
		"INGREDIENT_ANALYSIS":    true,
		"SUBJECTIVE_COMPARISON":  true,
	}
	return aiObjectives[intent.Objective]
}

// deterministicRank ranks products by price-per-unit without AI.
func deterministicRank(products []models.Product, intent models.QueryIntent) models.AnalyzeResponse {
	ranked := make([]models.RankedProduct, len(products))
	for i, p := range products {
		score := 0.0
		metric := ""
		if p.WeightG > 0 {
			score = p.PriceInr / p.WeightG
			metric = "₹/gram"
		} else if p.VolumeMl > 0 {
			score = p.PriceInr / p.VolumeMl
			metric = "₹/ml"
		} else {
			score = p.PriceInr
			metric = "₹"
		}
		ranked[i] = models.RankedProduct{
			Product:    p,
			Rank:       i + 1,
			Score:      score,
			Metric:     metric,
			Explanation: "",
		}
	}

	// Sort ascending by score (lower = better value)
	for i := 0; i < len(ranked); i++ {
		for j := i + 1; j < len(ranked); j++ {
			if ranked[j].Score < ranked[i].Score {
				ranked[i], ranked[j] = ranked[j], ranked[i]
			}
		}
	}
	for i := range ranked {
		ranked[i].Rank = i + 1
	}

	best := ranked[0]
	return models.AnalyzeResponse{
		RankedProducts: ranked,
		Explanation:    "Ranked by " + best.Metric + ". Best value: " + best.Product.Name,
		Confidence:     1.0,
		UsedAI:         false,
	}
}
