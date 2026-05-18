package com.example.enterpriserag.dto;

import java.util.List;

public record EvalCaseResult(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources,
        String expectedAnswer,
        boolean passed,
        EvalFailureReason failureReason,
        String failureMessage,
        List<Citation> retrieved
) {
}
