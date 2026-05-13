package com.example.enterpriserag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.enterpriserag.dto.DocumentSummary;
import com.example.enterpriserag.dto.IngestResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final DocumentChunker chunker;
    private final Map<String, DocumentSummary> summaries = Collections.synchronizedMap(new LinkedHashMap<>());

    public KnowledgeBaseService(VectorStore vectorStore, DocumentChunker chunker) {
        this.vectorStore = vectorStore;
        this.chunker = chunker;
    }

    public IngestResponse ingest(MultipartFile file) throws IOException {
        String filename = cleanFilename(file.getOriginalFilename());
        if (!isSupported(filename)) {
            throw new IllegalArgumentException("当前 MVP 只支持 .txt 和 .md 文档");
        }
        return ingest(filename, new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    public IngestResponse ingestSamplePolicy() throws IOException {
        ClassPathResource resource = new ClassPathResource("samples/company-policy.md");
        return ingest("company-policy.md", resource.getContentAsString(StandardCharsets.UTF_8));
    }

    public IngestResponse ingest(String source, String content) {
        List<String> chunks = chunker.split(content);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档内容为空，无法构建知识库");
        }

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", source);
            metadata.put("chunk", i + 1);
            metadata.put("indexedAt", Instant.now().toString());
            documents.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.add(documents);
        summaries.put(source, new DocumentSummary(source, chunks.size(), Instant.now()));
        return new IngestResponse(source, chunks.size(), "已写入知识库");
    }

    public List<DocumentSummary> listDocuments() {
        synchronized (summaries) {
            return new ArrayList<>(summaries.values());
        }
    }

    private static String cleanFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "uploaded.md";
        }
        return filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private static boolean isSupported(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md");
    }
}
