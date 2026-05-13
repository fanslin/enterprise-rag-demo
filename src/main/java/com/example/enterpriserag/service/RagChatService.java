package com.example.enterpriserag.service;

import java.util.List;
import java.util.stream.Collectors;

import com.example.enterpriserag.dto.ChatResponse;
import com.example.enterpriserag.dto.Citation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagChatService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final int topK;
    private final double similarityThreshold;

    public RagChatService(
            VectorStore vectorStore,
            ChatClient chatClient,
            @Value("${app.rag.top-k:4}") int topK,
            @Value("${app.rag.similarity-threshold:0.55}") double similarityThreshold
    ) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public ChatResponse ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        List<Document> retrieved = retrieve(question);
        if (retrieved.isEmpty()) {
            return new ChatResponse("知识库里没有检索到足够相关的资料。请先上传制度文档，或换一种问法。", List.of());
        }

        String context = buildContext(retrieved);
        String answer = chatClient.prompt()
                .system("""
                        你是企业制度知识库助手。请严格基于用户提供的“知识库片段”回答。
                        如果片段中没有答案，请直接说“根据当前知识库无法确认”，不要编造。
                        回答要简洁、准确，适合企业员工阅读。
                        最后必须列出“引用：”，格式为 来源文件#片段编号。
                        """)
                .user("""
                        问题：
                        %s

                        知识库片段：
                        %s
                        """.formatted(question, context))
                .call()
                .content();

        return new ChatResponse(answer, toCitations(retrieved));
    }

    public List<Document> retrieve(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(request);
    }

    public List<Citation> toCitations(List<Document> documents) {
        return documents.stream()
                .map(document -> new Citation(
                        metadataAsString(document, "source", "unknown"),
                        metadataAsInt(document, "chunk", 0),
                        preview(document.getText())
                ))
                .toList();
    }

    private static String buildContext(List<Document> documents) {
        return documents.stream()
                .map(document -> "[%s#%s]\n%s".formatted(
                        metadataAsString(document, "source", "unknown"),
                        metadataAsString(document, "chunk", "?"),
                        document.getText()
                ))
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private static String preview(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }

    private static String metadataAsString(Document document, String key, String fallback) {
        Object value = document.getMetadata().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int metadataAsInt(Document document, String key, int fallback) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
