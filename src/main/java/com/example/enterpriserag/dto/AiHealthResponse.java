package com.example.enterpriserag.dto;

public record AiHealthResponse(
        String mode,
        boolean chatConfigured,
        boolean embeddingConfigured,
        String message
) {
}
