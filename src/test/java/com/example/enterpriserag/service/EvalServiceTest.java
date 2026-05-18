package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.example.enterpriserag.dto.Citation;
import com.example.enterpriserag.dto.EvalFailureReason;
import com.example.enterpriserag.dto.EvalReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class EvalServiceTest {

    private final RagChatService ragChatService = mock(RagChatService.class);

    @BeforeEach
    void setUpCitationConversion() {
        when(ragChatService.toCitations(anyList())).thenAnswer(invocation -> {
            List<Document> documents = invocation.getArgument(0);
            return documents.stream()
                    .map(document -> new Citation(
                            String.valueOf(document.getMetadata().get("source")),
                            ((Number) document.getMetadata().get("chunk")).intValue(),
                            document.getText()
                    ))
                    .toList();
        });
    }

    @Test
    void runRetrievalEvalBuildsCartesianParameterRuns() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(2), eq(0.0))).thenReturn(List.of(matchingDocument()));
        when(ragChatService.retrieve(anyString(), eq(2), eq(0.3))).thenReturn(List.of(matchingDocument()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0))).thenReturn(List.of(matchingDocument()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.3))).thenReturn(List.of(matchingDocument()));

        EvalReport report = service.runRetrievalEval(List.of(2, 4), List.of(0.0, 0.3));

        assertThat(report.runs()).hasSize(4);
        assertThat(report.runs())
                .extracting(run -> "%s/%s".formatted(run.topK(), run.similarityThreshold()))
                .containsExactly("2/0.0", "2/0.3", "4/0.0", "4/0.3");
        verify(ragChatService, times(4)).retrieve(anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void runRetrievalEvalMarksNoRetrievedDocuments() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0))).thenReturn(List.of());

        EvalReport report = service.runRetrievalEval(List.of(4), List.of(0.0));

        assertThat(firstResult(report).passed()).isFalse();
        assertThat(firstResult(report).failureReason()).isEqualTo(EvalFailureReason.NO_RETRIEVED_DOCUMENTS);
        assertThat(firstResult(report).failureMessage()).isEqualTo("没有召回任何片段");
    }

    @Test
    void runRetrievalEvalMarksMissingKeywords() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0)))
                .thenReturn(List.of(document("其他内容", "company-policy.md")));

        EvalReport report = service.runRetrievalEval(List.of(4), List.of(0.0));

        assertThat(firstResult(report).passed()).isFalse();
        assertThat(firstResult(report).failureReason()).isEqualTo(EvalFailureReason.MISSING_KEYWORDS);
        assertThat(firstResult(report).failureMessage()).isEqualTo("召回片段未命中期望关键词");
    }

    @Test
    void runRetrievalEvalMarksMissingSource() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0)))
                .thenReturn(List.of(document("发票需要在 30 天内提交", "other-policy.md")));

        EvalReport report = service.runRetrievalEval(List.of(4), List.of(0.0));

        assertThat(firstResult(report).passed()).isFalse();
        assertThat(firstResult(report).failureReason()).isEqualTo(EvalFailureReason.MISSING_SOURCE);
        assertThat(firstResult(report).failureMessage()).isEqualTo("召回来源未命中期望来源");
    }

    @Test
    void runRetrievalEvalMarksPassedWhenKeywordAndSourceMatch() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0))).thenReturn(List.of(matchingDocument()));

        EvalReport report = service.runRetrievalEval(List.of(4), List.of(0.0));

        assertThat(firstResult(report).passed()).isTrue();
        assertThat(firstResult(report).failureReason()).isEqualTo(EvalFailureReason.PASSED);
        assertThat(firstResult(report).failureMessage()).isEqualTo("通过");
        assertThat(firstResult(report).retrieved()).hasSize(1);
    }

    @Test
    void runRetrievalEvalAggregatesSummary() {
        EvalService service = new EvalService(ragChatService, List.of(defaultCase()));
        when(ragChatService.retrieve(anyString(), eq(2), eq(0.0))).thenReturn(List.of(matchingDocument()));
        when(ragChatService.retrieve(anyString(), eq(4), eq(0.0))).thenReturn(List.of());

        EvalReport report = service.runRetrievalEval(List.of(2, 4), List.of(0.0));

        assertThat(report.summary().totalRuns()).isEqualTo(2);
        assertThat(report.summary().totalCases()).isEqualTo(2);
        assertThat(report.summary().passedCases()).isEqualTo(1);
        assertThat(report.summary().failedCases()).isEqualTo(1);
        assertThat(report.summary().passRate()).isEqualTo(0.5);
        assertThat(report.runs().get(0).passRate()).isEqualTo(1.0);
        assertThat(report.runs().get(1).passRate()).isZero();
    }

    private static EvalCase defaultCase() {
        return new EvalCase(
                "invoice-deadline",
                "报销发票最晚什么时候提交？",
                List.of("30"),
                List.of("company-policy.md"),
                "发票需要在 30 天内提交。"
        );
    }

    private static Document matchingDocument() {
        return document("发票需要在 30 天内提交", "company-policy.md");
    }

    private static Document document(String text, String source) {
        return new Document("doc-1", text, Map.of("source", source, "chunk", 1));
    }

    private static com.example.enterpriserag.dto.EvalCaseResult firstResult(EvalReport report) {
        return report.runs().get(0).results().get(0);
    }
}
