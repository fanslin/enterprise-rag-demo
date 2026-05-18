package com.example.enterpriserag.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.example.enterpriserag.dto.DocumentSummary;
import com.example.enterpriserag.dto.RebuildResponse;
import com.example.enterpriserag.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentControllerTest {

    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DocumentController(knowledgeBaseService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void deleteDocumentDelegatesAndReturnsDeletedSummary() throws Exception {
        when(knowledgeBaseService.deleteDocument("company-policy.md"))
                .thenReturn(new DocumentSummary("company-policy.md", 4, Instant.parse("2026-05-18T10:00:00Z")));

        mockMvc.perform(delete("/api/documents/company-policy.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("company-policy.md"))
                .andExpect(jsonPath("$.chunks").value(4));

        verify(knowledgeBaseService).deleteDocument("company-policy.md");
    }

    @Test
    void rebuildIndexDelegatesAndReturnsCounts() throws Exception {
        when(knowledgeBaseService.rebuildIndex())
                .thenReturn(new RebuildResponse(2, 7, "已重建 2 个文档，共 7 个片段"));

        mockMvc.perform(post("/api/documents/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(2))
                .andExpect(jsonPath("$.chunks").value(7))
                .andExpect(jsonPath("$.message").value("已重建 2 个文档，共 7 个片段"));

        verify(knowledgeBaseService).rebuildIndex();
    }
}
