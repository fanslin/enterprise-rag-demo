package com.example.enterpriserag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.enterpriserag.dto.DocumentSummary;
import com.example.enterpriserag.dto.IngestResponse;
import com.example.enterpriserag.dto.RebuildResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final DocumentChunker chunker;
    private final Map<String, IndexedDocument> documentsBySource = Collections.synchronizedMap(new LinkedHashMap<>());

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
        BuiltDocuments builtDocuments = buildDocuments(source, content, Instant.now());
        if (builtDocuments.documents().isEmpty()) {
            throw new IllegalArgumentException("文档内容为空，无法构建知识库");
        }

        IndexedDocument oldDocument = documentsBySource.get(source);
        if (oldDocument != null) {
            vectorStore.delete(oldDocument.vectorIds());
        }
        try {
            vectorStore.add(builtDocuments.documents());
        } catch (RuntimeException failure) {
            restoreVectorDocuments(oldDocument == null ? List.of() : List.of(oldDocument), failure);
            throw failure;
        }
        documentsBySource.put(source, builtDocuments.toIndexedDocument(content));
        return new IngestResponse(source, builtDocuments.summary().chunks(), "已写入知识库");
    }

    public List<DocumentSummary> listDocuments() {
        synchronized (documentsBySource) {
            return documentsBySource.values().stream()
                    .map(IndexedDocument::summary)
                    .toList();
        }
    }

    public DocumentSummary deleteDocument(String source) {
        String cleanSource = cleanFilename(source);
        synchronized (documentsBySource) {
            IndexedDocument document = documentsBySource.get(cleanSource);
            if (document == null) {
                throw new IllegalArgumentException("文档不存在：" + cleanSource);
            }
            vectorStore.delete(document.vectorIds());
            documentsBySource.remove(cleanSource);
            return document.summary();
        }
    }

    public RebuildResponse rebuildIndex() {
        synchronized (documentsBySource) {
            if (documentsBySource.isEmpty()) {
                return new RebuildResponse(0, 0, "知识库为空，无需重建");
            }

            List<String> oldVectorIds = documentsBySource.values().stream()
                    .flatMap(document -> document.vectorIds().stream())
                    .toList();
            vectorStore.delete(oldVectorIds);

            List<IndexedDocument> oldDocuments = new ArrayList<>(documentsBySource.values());
            List<Document> rebuiltDocuments = new ArrayList<>();
            Map<String, IndexedDocument> rebuiltRecords = new LinkedHashMap<>();
            int totalChunks = 0;

            for (IndexedDocument indexedDocument : oldDocuments) {
                String source = indexedDocument.source();
                BuiltDocuments builtDocuments = buildDocuments(source, indexedDocument.content(), Instant.now());
                rebuiltDocuments.addAll(builtDocuments.documents());
                totalChunks += builtDocuments.summary().chunks();
                rebuiltRecords.put(source, builtDocuments.toIndexedDocument(indexedDocument.content()));
            }

            try {
                vectorStore.add(rebuiltDocuments);
            } catch (RuntimeException failure) {
                restoreVectorDocuments(oldDocuments, failure);
                throw failure;
            }
            documentsBySource.clear();
            documentsBySource.putAll(rebuiltRecords);
            int totalDocuments = documentsBySource.size();
            return new RebuildResponse(
                    totalDocuments,
                    totalChunks,
                    "已重建 " + totalDocuments + " 个文档，共 " + totalChunks + " 个片段"
            );
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

    private BuiltDocuments buildDocuments(String source, String content, Instant indexedAt) {
        List<String> chunks = chunker.split(content);
        List<Document> documents = new ArrayList<>();
        List<String> vectorIds = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            int chunkNumber = i + 1;
            String vectorId = vectorId(source, chunkNumber);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", source);
            metadata.put("chunk", chunkNumber);
            metadata.put("indexedAt", indexedAt.toString());
            documents.add(new Document(vectorId, chunks.get(i), metadata));
            vectorIds.add(vectorId);
        }
        return new BuiltDocuments(
                source,
                List.copyOf(documents),
                List.copyOf(vectorIds),
                new DocumentSummary(source, chunks.size(), indexedAt)
        );
    }

    private void restoreVectorDocuments(List<IndexedDocument> documents, RuntimeException originalFailure) {
        List<Document> restoreDocuments = documents.stream()
                .filter(document -> document != null)
                .flatMap(document -> buildDocuments(
                        document.source(),
                        document.content(),
                        document.summary().indexedAt()
                ).documents().stream())
                .toList();
        if (restoreDocuments.isEmpty()) {
            return;
        }
        try {
            vectorStore.add(restoreDocuments);
        } catch (RuntimeException restoreFailure) {
            originalFailure.addSuppressed(restoreFailure);
        }
    }

    private static String vectorId(String source, int chunkNumber) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(source.getBytes(StandardCharsets.UTF_8))
                + "::chunk-" + chunkNumber;
    }

    private record IndexedDocument(
            String source,
            String content,
            List<String> vectorIds,
            DocumentSummary summary
    ) {
    }

    private record BuiltDocuments(
            String source,
            List<Document> documents,
            List<String> vectorIds,
            DocumentSummary summary
    ) {

        IndexedDocument toIndexedDocument(String content) {
            return new IndexedDocument(source, content, vectorIds, summary);
        }
    }
}
