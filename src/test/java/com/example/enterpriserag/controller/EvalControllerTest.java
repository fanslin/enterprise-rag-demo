package com.example.enterpriserag.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.enterpriserag.dto.EvalReport;
import com.example.enterpriserag.dto.EvalRunResult;
import com.example.enterpriserag.dto.EvalSummary;
import com.example.enterpriserag.service.EvalService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvalControllerTest {

    private final EvalService evalService = mock(EvalService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new EvalController(evalService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void runReturnsReportForSingleParameterSet() throws Exception {
        when(evalService.runRetrievalEval(eq(List.of(4)), eq(List.of(0.0))))
                .thenReturn(report(1, 4, 0.0));

        mockMvc.perform(get("/api/eval")
                        .param("topK", "4")
                        .param("similarityThreshold", "0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRuns").value(1))
                .andExpect(jsonPath("$.runs[0].topK").value(4))
                .andExpect(jsonPath("$.runs[0].similarityThreshold").value(0.0));

        verify(evalService).runRetrievalEval(List.of(4), List.of(0.0));
    }

    @Test
    void runAcceptsCommaSeparatedParameters() throws Exception {
        when(evalService.runRetrievalEval(eq(List.of(2, 4)), eq(List.of(0.0, 0.3))))
                .thenReturn(report(4, 2, 0.0));

        mockMvc.perform(get("/api/eval")
                        .param("topK", "2,4")
                        .param("similarityThreshold", "0,0.3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRuns").value(4));

        verify(evalService).runRetrievalEval(List.of(2, 4), List.of(0.0, 0.3));
    }

    @Test
    void runRejectsInvalidTopKFormat() throws Exception {
        mockMvc.perform(get("/api/eval").param("topK", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("topK 参数格式错误：abc"));
    }

    @Test
    void runRejectsInvalidThresholdFormat() throws Exception {
        mockMvc.perform(get("/api/eval").param("similarityThreshold", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("similarityThreshold 参数格式错误：abc"));
    }

    private static EvalReport report(int totalRuns, Integer topK, Double similarityThreshold) {
        return new EvalReport(
                new EvalSummary(totalRuns, 0, 0, 0, 0.0),
                List.of(new EvalRunResult(topK, similarityThreshold, 0, 0, 0, 0.0, List.of()))
        );
    }
}
