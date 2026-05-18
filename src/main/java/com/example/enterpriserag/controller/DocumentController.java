package com.example.enterpriserag.controller;

import java.io.IOException;
import java.util.List;

import com.example.enterpriserag.dto.DocumentSummary;
import com.example.enterpriserag.dto.IngestResponse;
import com.example.enterpriserag.dto.RebuildResponse;
import com.example.enterpriserag.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    IngestResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return knowledgeBaseService.ingest(file);
    }

    @PostMapping("/sample")
    IngestResponse ingestSample() throws IOException {
        return knowledgeBaseService.ingestSamplePolicy();
    }

    @PostMapping("/rebuild")
    RebuildResponse rebuildIndex() {
        return knowledgeBaseService.rebuildIndex();
    }

    @GetMapping
    List<DocumentSummary> listDocuments() {
        return knowledgeBaseService.listDocuments();
    }

    @DeleteMapping("/{source}")
    DocumentSummary deleteDocument(@PathVariable String source) {
        return knowledgeBaseService.deleteDocument(source);
    }
}
