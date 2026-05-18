package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.calls;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.example.enterpriserag.dto.DocumentSummary;
import com.example.enterpriserag.dto.IngestResponse;
import com.example.enterpriserag.dto.RebuildResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @Test
    void ingestSameSourceDeletesOldChunksBeforeAddingReplacement() {
        service.ingest("policy.md", "第一版制度内容。");

        ArgumentCaptor<List<Document>> firstAddCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(firstAddCaptor.capture());
        List<String> firstVectorIds = firstAddCaptor.getValue().stream()
                .map(Document::getId)
                .toList();
        clearInvocations(vectorStore);

        IngestResponse replacement = service.ingest("policy.md", "第二版制度内容，旧片段不应该继续保留。");

        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(firstVectorIds);
        inOrder.verify(vectorStore).add(anyList());

        assertThat(replacement.source()).isEqualTo("policy.md");
        assertThat(service.listDocuments())
                .hasSize(1)
                .first()
                .satisfies(summary -> {
                    assertThat(summary.source()).isEqualTo("policy.md");
                    assertThat(summary.chunks()).isEqualTo(replacement.chunks());
                });
    }

    @Test
    void deleteDocumentRemovesVectorIdsAndState() {
        service.ingest("policy.md", "制度内容。");
        List<String> vectorIds = captureAddedVectorIds();
        clearInvocations(vectorStore);

        var deleted = service.deleteDocument("policy.md");

        assertThat(deleted.source()).isEqualTo("policy.md");
        assertThat(service.listDocuments()).isEmpty();
        verify(vectorStore).delete(vectorIds);
    }

    @Test
    void deleteUnknownDocumentFailsBeforeTouchingVectorStore() {
        assertThatThrownBy(() -> service.deleteDocument("missing.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("文档不存在：missing.md");

        verifyNoInteractions(vectorStore);
    }

    @Test
    void rebuildIndexDeletesKnownIdsAndAddsDocumentsFromCachedContent() {
        service.ingest("policy-a.md", "第一份制度内容。");
        service.ingest("policy-b.md", "第二份制度内容。");
        List<String> oldVectorIds = captureAddedVectorIds(2);
        clearInvocations(vectorStore);

        RebuildResponse response = service.rebuildIndex();

        ArgumentCaptor<List<Document>> rebuiltDocumentsCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(oldVectorIds);
        inOrder.verify(vectorStore).add(rebuiltDocumentsCaptor.capture());

        assertThat(response.documents()).isEqualTo(2);
        assertThat(response.chunks()).isEqualTo(2);
        assertThat(response.message()).isEqualTo("已重建 2 个文档，共 2 个片段");
        assertThat(rebuiltDocumentsCaptor.getValue())
                .extracting(document -> document.getMetadata().get("source"))
                .containsExactly("policy-a.md", "policy-b.md");
        assertThat(service.listDocuments()).hasSize(2);
    }

    @Test
    void rebuildEmptyKnowledgeBaseReturnsZeroCounts() {
        RebuildResponse response = service.rebuildIndex();

        assertThat(response.documents()).isZero();
        assertThat(response.chunks()).isZero();
        assertThat(response.message()).isEqualTo("知识库为空，无需重建");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void replacementAddFailureRestoresOldVectorDocuments() {
        service.ingest("policy.md", "第一版制度内容。");
        List<Document> oldDocuments = captureAddedDocuments(1);
        List<String> oldVectorIds = oldDocuments.stream().map(Document::getId).toList();
        clearInvocations(vectorStore);
        RuntimeException failure = new RuntimeException("embedding down");
        doThrow(failure).doNothing().when(vectorStore).add(anyList());

        assertThatThrownBy(() -> service.ingest("policy.md", "第二版制度内容。"))
                .isSameAs(failure);

        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(oldVectorIds);
        inOrder.verify(vectorStore, calls(2)).add(anyList());

        ArgumentCaptor<List<Document>> addCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(addCaptor.capture());
        assertThat(addCaptor.getAllValues().get(1))
                .extracting(Document::getId)
                .containsExactlyElementsOf(oldVectorIds);
        assertThat(service.listDocuments())
                .hasSize(1)
                .first()
                .satisfies(summary -> assertThat(summary.source()).isEqualTo("policy.md"));
    }

    @Test
    void rebuildAddFailureRestoresOldVectorDocuments() {
        service.ingest("policy-a.md", "第一份制度内容。");
        service.ingest("policy-b.md", "第二份制度内容。");
        List<Document> oldDocuments = captureAddedDocuments(2);
        List<String> oldVectorIds = oldDocuments.stream().map(Document::getId).toList();
        clearInvocations(vectorStore);
        RuntimeException failure = new RuntimeException("embedding down");
        doThrow(failure).doNothing().when(vectorStore).add(anyList());

        assertThatThrownBy(service::rebuildIndex)
                .isSameAs(failure);

        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(oldVectorIds);
        inOrder.verify(vectorStore, calls(2)).add(anyList());

        ArgumentCaptor<List<Document>> addCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(addCaptor.capture());
        assertThat(addCaptor.getAllValues().get(1))
                .extracting(Document::getId)
                .containsExactlyElementsOf(oldVectorIds);
        assertThat(service.listDocuments())
                .extracting(DocumentSummary::source)
                .containsExactly("policy-a.md", "policy-b.md");
    }

    private List<String> captureAddedVectorIds() {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());
        return captor.getAllValues().stream()
                .flatMap(List::stream)
                .map(Document::getId)
                .toList();
    }

    private List<String> captureAddedVectorIds(int addCalls) {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(addCalls)).add(captor.capture());
        List<String> ids = new ArrayList<>();
        for (List<Document> documents : captor.getAllValues()) {
            ids.addAll(documents.stream().map(Document::getId).toList());
        }
        return ids;
    }

    private List<Document> captureAddedDocuments(int addCalls) {
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(addCalls)).add(captor.capture());
        List<Document> documents = new ArrayList<>();
        for (List<Document> addedDocuments : captor.getAllValues()) {
            documents.addAll(addedDocuments);
        }
        return documents;
    }
}
