package main

import (
	"encoding/json"
	"log"
	"math/rand"
	"net/http"
	"strings"
	"time"
)

// OpenAI-compatible request/response structures
type EmbeddingRequest struct {
	Model string `json:"model"`
	Input string `json:"input"`
}

type EmbeddingResponse struct {
	Data []struct {
		Embedding []float32 `json:"embedding"`
	} `json:"data"`
}

type ChatRequest struct {
	Model    string `json:"model"`
	Messages []struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	} `json:"messages"`
}

type ChatResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
}

func main() {
	rand.Seed(time.Now().UnixNano())

	// Embeddings endpoint
	http.HandleFunc("/v1/embeddings", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		var req EmbeddingRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		// Generate mock embeddings based on model
		dimension := 1536 // Default for text-embedding-3-small
		if strings.Contains(req.Model, "large") {
			dimension = 3072
		}

		// Create deterministic embeddings based on input
		embeddings := generateMockEmbeddings(req.Input, dimension)

		resp := EmbeddingResponse{
			Data: []struct {
				Embedding []float32 `json:"embedding"`
			}{
				{Embedding: embeddings},
			},
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
		log.Printf("Generated embeddings for model %s, input length: %d", req.Model, len(req.Input))
	})

	// Chat completions endpoint (for summaries)
	http.HandleFunc("/v1/chat/completions", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
			return
		}

		var req ChatRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		// Generate mock summary
		userContent := ""
		for _, msg := range req.Messages {
			if msg.Role == "user" {
				userContent = msg.Content
				break
			}
		}

		summary := generateMockSummary(userContent)

		resp := ChatResponse{
			Choices: []struct {
				Message struct {
					Content string `json:"content"`
				} `json:"message"`
			}{
				{
					Message: struct {
						Content string `json:"content"`
					}{
						Content: summary,
					},
				},
			},
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(resp)
		log.Printf("Generated summary for model %s", req.Model)
	})

	// Health check
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	})

	port := ":8081"
	log.Printf("Mock LLM service starting on port %s", port)
	log.Fatal(http.ListenAndServe(port, nil))
}

func generateMockEmbeddings(input string, dimension int) []float32 {
	// Create deterministic embeddings based on input
	// Use simple hash of input to seed random number generator
	hash := 0
	for _, ch := range input {
		hash = hash*31 + int(ch)
	}
	rand.Seed(int64(hash))

	embeddings := make([]float32, dimension)
	for i := range embeddings {
		// Generate values between -1 and 1
		embeddings[i] = (rand.Float32() * 2) - 1
	}

	// Normalize the vector
	var sum float32
	for _, val := range embeddings {
		sum += val * val
	}
	norm := float32(1.0 / sqrt(float64(sum)))
	for i := range embeddings {
		embeddings[i] *= norm
	}

	return embeddings
}

func generateMockSummary(content string) string {
	// Generate different summaries based on content characteristics
	contentLower := strings.ToLower(content)
	
	if strings.Contains(contentLower, "technical specification") {
		return "This technical specification document outlines the key requirements and implementation details for the system. It covers architecture, performance metrics, and integration points."
	}
	
	if strings.Contains(contentLower, "project proposal") {
		return "The project proposal presents a comprehensive plan for implementing the new system, including timeline, budget estimates, and expected deliverables across multiple phases."
	}
	
	if strings.Contains(contentLower, "meeting notes") {
		return "Meeting notes capture key decisions and action items from the team discussion. Main topics included project status updates and upcoming milestone planning."
	}
	
	if strings.Contains(contentLower, "annual report") {
		return "The annual report provides a comprehensive overview of the organization's performance, highlighting key achievements, financial results, and strategic initiatives for the upcoming year."
	}
	
	if strings.Contains(contentLower, "research paper") {
		return "This research paper investigates novel approaches to the problem domain, presenting empirical findings and theoretical contributions to advance the field of study."
	}
	
	// Generic summary for other content
	words := strings.Fields(content)
	wordCount := len(words)
	
	if wordCount < 50 {
		return "This brief document contains preliminary information that requires further elaboration to provide comprehensive insights."
	}
	
	if wordCount < 200 {
		return "The document provides a concise overview of the subject matter, covering essential points while maintaining clarity and focus on key objectives."
	}
	
	return "This comprehensive document explores multiple aspects of the topic, providing detailed analysis and recommendations. Key themes include process optimization, stakeholder engagement, and measurable outcomes."
}

func sqrt(x float64) float64 {
	// Simple square root approximation
	if x == 0 {
		return 0
	}
	
	guess := x / 2
	for i := 0; i < 10; i++ {
		guess = (guess + x/guess) / 2
	}
	return guess
}