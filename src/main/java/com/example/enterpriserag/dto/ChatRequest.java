package com.example.enterpriserag.dto;

public record ChatRequest(String question, Integer topK, Double similarityThreshold) {
}
