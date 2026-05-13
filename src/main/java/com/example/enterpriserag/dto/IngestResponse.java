package com.example.enterpriserag.dto;

public record IngestResponse(String source, int chunks, String message) {
}
