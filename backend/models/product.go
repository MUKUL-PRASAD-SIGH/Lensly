package models

// Product represents a parsed product from a shopping app screen.
// All fields come from the Android on-device parser — never raw screen data.
type Product struct {
	Name               string  `json:"name"`
	PriceInr           float64 `json:"price_inr"`
	WeightG            float64 `json:"weight_g,omitempty"`
	VolumeMl           float64 `json:"volume_ml,omitempty"`
	MrpCrossedOut      float64 `json:"mrp_crossed_out,omitempty"`
	DiscountPercent    float64 `json:"discount_percent,omitempty"`
	Rating             float64 `json:"rating,omitempty"`
	IngredientsAvail   bool    `json:"ingredients_available"`
	DiscountVerified   bool    `json:"discount_verified"`
	SourceApp          string  `json:"source_app"` // "zepto", "blinkit", "amazon"
}

// RankedProduct is a Product with ranking metadata added.
type RankedProduct struct {
	Product
	Rank        int     `json:"rank"`
	Score       float64 `json:"score"`       // Lower is better (₹/gram, ₹/ml)
	Metric      string  `json:"metric"`      // "₹/gram", "₹/ml", "₹"
	Explanation string  `json:"explanation"` // Human-readable reason
	FakeDiscount bool   `json:"fake_discount_warning,omitempty"`
}

// QueryIntent is the structured output of on-device intent classification.
type QueryIntent struct {
	Objective  string  `json:"objective"`           // "MINIMIZE_PRICE_PER_UNIT", "MAXIMIZE_HEALTH_SCORE", etc.
	Category   string  `json:"category"`            // "toothpaste", "protein_powder", etc.
	MaxPrice   float64 `json:"max_price,omitempty"` // User constraint
	RawQuery   string  `json:"raw_query"`           // Original user query text
}

// UserPreferences is the anonymized on-device user profile.
type UserPreferences struct {
	BudgetRange       string   `json:"budget_range"`        // "low", "mid", "high"
	AvoidIngredients  []string `json:"avoid_ingredients"`   // ["SLS", "palm_oil"]
	BrandAffinity     []string `json:"brand_affinity"`
}

// Objective constants
const (
	ObjectiveMinPricePerUnit   = "MINIMIZE_PRICE_PER_UNIT"
	ObjectiveMaxHealthScore    = "MAXIMIZE_HEALTH_SCORE"
	ObjectiveBestOverall       = "BEST_OVERALL"
	ObjectiveBestValue         = "BEST_VALUE"
	ObjectiveIngredientAnalysis = "INGREDIENT_ANALYSIS"
	ObjectiveLongestDuration   = "LONGEST_DURATION"
)

// AnalyzeResponse is returned with ranked results.
type AnalyzeResponse struct {
	RankedProducts []RankedProduct `json:"ranked_products"`
	Explanation    string          `json:"explanation"`
	Confidence     float64         `json:"confidence"`
	UsedAI         bool            `json:"used_ai"`
}

