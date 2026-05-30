package handlers

import (
	"encoding/json"
	"net/http"
	"time"
)

// HealthResponse is the response for the health check endpoint.
type HealthResponse struct {
	Status    string `json:"status"`
	Timestamp string `json:"timestamp"`
	Version   string `json:"version"`
}

// Health handles GET /health
func Health(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(HealthResponse{
		Status:    "ok",
		Timestamp: time.Now().UTC().Format(time.RFC3339),
		Version:   "1.0.0",
	})
}
