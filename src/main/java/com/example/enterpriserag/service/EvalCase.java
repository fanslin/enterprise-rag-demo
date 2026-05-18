package com.example.enterpriserag.service;

import java.util.List;

public record EvalCase(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources,
        String expectedAnswer
) {
}
