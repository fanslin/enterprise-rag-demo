package com.example.enterpriserag.dto;

import java.util.List;

public record EvalReport(
        EvalSummary summary,
        List<EvalRunResult> runs
) {
}
