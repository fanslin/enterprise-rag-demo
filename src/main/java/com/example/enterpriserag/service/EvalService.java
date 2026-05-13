package com.example.enterpriserag.service;

import java.util.List;

import com.example.enterpriserag.dto.EvalCaseResult;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Service
public class EvalService {

    private static final List<EvalCase> CASES = List.of(
            new EvalCase("试用期请假会影响转正吗？", "转正"),
            new EvalCase("报销发票最晚什么时候提交？", "30"),
            new EvalCase("年假没休完怎么处理？", "结转"),
            new EvalCase("远程办公需要提前多久申请？", "1")
    );

    private final RagChatService ragChatService;

    public EvalService(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    public List<EvalCaseResult> runRetrievalEval() {
        return CASES.stream()
                .map(testCase -> {
                    List<Document> retrieved = ragChatService.retrieve(testCase.question());
                    boolean passed = retrieved.stream()
                            .anyMatch(document -> document.getText().contains(testCase.expectedKeyword()));
                    return new EvalCaseResult(
                            testCase.question(),
                            testCase.expectedKeyword(),
                            passed,
                            ragChatService.toCitations(retrieved)
                    );
                })
                .toList();
    }

    private record EvalCase(String question, String expectedKeyword) {
    }
}
