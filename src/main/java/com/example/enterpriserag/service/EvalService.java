package com.example.enterpriserag.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.enterpriserag.dto.EvalCaseResult;
import com.example.enterpriserag.dto.EvalFailureReason;
import com.example.enterpriserag.dto.EvalReport;
import com.example.enterpriserag.dto.EvalRunResult;
import com.example.enterpriserag.dto.EvalSummary;
import com.example.enterpriserag.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EvalService {

    private final RagChatService ragChatService;
    private final List<EvalCase> cases;

    @Autowired
    public EvalService(RagChatService ragChatService, EvalCaseLoader evalCaseLoader) {
        this(ragChatService, evalCaseLoader.loadCases());
    }

    EvalService(RagChatService ragChatService, List<EvalCase> cases) {
        this.ragChatService = ragChatService;
        this.cases = List.copyOf(cases);
    }

    public EvalReport runRetrievalEval() {
        return runRetrievalEval(Collections.singletonList(null), Collections.singletonList(null));
    }

    public EvalReport runRetrievalEval(Integer topK, Double similarityThreshold) {
        return runRetrievalEval(Collections.singletonList(topK), Collections.singletonList(similarityThreshold));
    }

    public EvalReport runRetrievalEval(List<Integer> topKValues, List<Double> similarityThresholdValues) {
        List<Integer> resolvedTopKValues = emptyToDefault(topKValues);
        List<Double> resolvedSimilarityThresholdValues = emptyToDefault(similarityThresholdValues);
        List<EvalRunResult> runs = new ArrayList<>();

        for (Integer topK : resolvedTopKValues) {
            for (Double similarityThreshold : resolvedSimilarityThresholdValues) {
                runs.add(runSingle(topK, similarityThreshold));
            }
        }

        int totalCases = runs.stream().mapToInt(EvalRunResult::totalCases).sum();
        int passedCases = runs.stream().mapToInt(EvalRunResult::passedCases).sum();
        int failedCases = totalCases - passedCases;
        return new EvalReport(
                new EvalSummary(
                        runs.size(),
                        totalCases,
                        passedCases,
                        failedCases,
                        passRate(passedCases, totalCases)
                ),
                runs
        );
    }

    private EvalRunResult runSingle(Integer topK, Double similarityThreshold) {
        List<EvalCaseResult> results = cases.stream()
                .map(testCase -> evaluate(testCase, topK, similarityThreshold))
                .toList();
        int passedCases = (int) results.stream().filter(EvalCaseResult::passed).count();
        int totalCases = results.size();
        int failedCases = totalCases - passedCases;
        return new EvalRunResult(
                topK,
                similarityThreshold,
                totalCases,
                passedCases,
                failedCases,
                passRate(passedCases, totalCases),
                results
        );
    }

    private EvalCaseResult evaluate(EvalCase testCase, Integer topK, Double similarityThreshold) {
        List<Document> retrieved = ragChatService.retrieve(
                testCase.question(),
                topK,
                similarityThreshold
        );
        List<Citation> citations = ragChatService.toCitations(retrieved);
        EvalFailureReason failureReason = failureReason(testCase, retrieved, citations);
        boolean passed = failureReason == EvalFailureReason.PASSED;
        return new EvalCaseResult(
                testCase.id(),
                testCase.question(),
                testCase.expectedKeywords(),
                testCase.expectedSources(),
                testCase.expectedAnswer(),
                passed,
                failureReason,
                failureMessage(failureReason),
                citations
        );
    }

    private static EvalFailureReason failureReason(
            EvalCase testCase,
            List<Document> retrieved,
            List<Citation> citations
    ) {
        if (retrieved.isEmpty()) {
            return EvalFailureReason.NO_RETRIEVED_DOCUMENTS;
        }
        boolean keywordMatched = testCase.expectedKeywords().stream()
                .anyMatch(keyword -> retrieved.stream()
                        .anyMatch(document -> document.getText().contains(keyword)));
        if (!keywordMatched) {
            return EvalFailureReason.MISSING_KEYWORDS;
        }
        boolean sourceMatched = testCase.expectedSources().isEmpty()
                || testCase.expectedSources().stream()
                .anyMatch(expectedSource -> citations.stream()
                        .anyMatch(citation -> citation.source().equals(expectedSource)));
        if (!sourceMatched) {
            return EvalFailureReason.MISSING_SOURCE;
        }
        return EvalFailureReason.PASSED;
    }

    private static String failureMessage(EvalFailureReason failureReason) {
        return switch (failureReason) {
            case PASSED -> "通过";
            case NO_RETRIEVED_DOCUMENTS -> "没有召回任何片段";
            case MISSING_KEYWORDS -> "召回片段未命中期望关键词";
            case MISSING_SOURCE -> "召回来源未命中期望来源";
        };
    }

    private static double passRate(int passedCases, int totalCases) {
        if (totalCases == 0) {
            return 0.0;
        }
        return (double) passedCases / totalCases;
    }

    private static <T> List<T> emptyToDefault(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.singletonList(null);
        }
        return values;
    }
}
