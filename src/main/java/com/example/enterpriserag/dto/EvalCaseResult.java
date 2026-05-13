package com.example.enterpriserag.dto;

import java.util.List;

public record EvalCaseResult(
        String question,
        String expectedKeyword,
        boolean passed,
        List<Citation> retrieved
) {
}
