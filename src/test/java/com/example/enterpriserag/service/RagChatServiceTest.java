package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.example.enterpriserag.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

class RagChatServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final RagChatService service = new RagChatService(vectorStore, chatClient, 4, 0.0);

    @Test
    void retrieveUsesConfiguredDefaultsWhenNoOverridesAreProvided() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.retrieve("报销规则", null, null);

        SearchRequest request = capturedSearchRequest();
        assertThat(request.getQuery()).isEqualTo("报销规则");
        assertThat(request.getTopK()).isEqualTo(4);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.0);
    }

    @Test
    void retrieveUsesRequestOverridesWhenProvided() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.retrieve("报销规则", 8, 0.42);

        SearchRequest request = capturedSearchRequest();
        assertThat(request.getTopK()).isEqualTo(8);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.42);
    }

    @Test
    void retrieveRejectsInvalidOverrides() {
        assertThatThrownBy(() -> service.retrieve("报销规则", 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topK 必须在 1 到 20 之间");

        assertThatThrownBy(() -> service.retrieve("报销规则", null, 1.2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("相似度阈值必须在 0.0 到 1.0 之间");

        verifyNoInteractions(vectorStore);
    }

    @Test
    void askReturnsEmptyKnowledgeBaseMessageWithoutCallingChatModel() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ChatResponse response = service.ask("年假规则", 3, 0.3);

        assertThat(response.answer()).contains("知识库里没有检索到足够相关的资料");
        assertThat(response.citations()).isEmpty();
        SearchRequest request = capturedSearchRequest();
        assertThat(request.getTopK()).isEqualTo(3);
        assertThat(request.getSimilarityThreshold()).isEqualTo(0.3);
        verifyNoInteractions(chatClient);
    }

    private SearchRequest capturedSearchRequest() {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }
}
