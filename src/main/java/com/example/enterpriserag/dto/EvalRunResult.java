package com.example.enterpriserag.dto;

import java.util.List;

public record EvalRunResult(
        Integer topK,
        Double similarityThreshold,
        int totalCases,
        int passedCases,
        int failedCases,
        double passRate,
        List<EvalCaseResult> results
) {
}
