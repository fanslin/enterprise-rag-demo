package com.example.enterpriserag.dto;

public record EvalSummary(
        int totalRuns,
        int totalCases,
        int passedCases,
        int failedCases,
        double passRate
) {
}
