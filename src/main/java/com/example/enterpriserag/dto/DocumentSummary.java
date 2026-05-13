package com.example.enterpriserag.dto;

import java.time.Instant;

public record DocumentSummary(String source, int chunks, Instant indexedAt) {
}
