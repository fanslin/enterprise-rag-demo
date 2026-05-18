package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.example.enterpriserag.dto.IngestResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

class KnowledgeBaseServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeBaseService service = new KnowledgeBaseService(vectorStore, new DocumentChunker());

    @Test
    void ingestAddsDocumentsWithCleanSourceAndMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../company-policy.md",
                "text/markdown",
                "第一章 报销规则。发票 30 天内提交。".getBytes(StandardCharsets.UTF_8)
        );

        IngestResponse response = service.ingest(file);

        assertThat(response.source()).isEqualTo("company-policy.md");
        assertThat(response.chunks()).isEqualTo(1);
        assertThat(service.listDocuments()).hasSize(1);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().get(0);
        assertThat(document.getText()).contains("发票 30 天内提交");
        assertThat(document.getMetadata())
                .containsEntry("source", "company-policy.md")
                .containsEntry("chunk", 1)
                .containsKey("indexedAt");
    }

    @Test
    void ingestRejectsUnsupportedFileTypesBeforeEmbedding() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policy.pdf",
                "application/pdf",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.ingest(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前 MVP 只支持 .txt 和 .md 文档");

        verifyNoInteractions(vectorStore);
    }
}
