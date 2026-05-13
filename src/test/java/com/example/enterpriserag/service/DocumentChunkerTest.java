package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void splitsLongTextWithOverlap() {
        String text = "第一段内容。".repeat(80) + "\n\n第二段内容。".repeat(80);

        List<String> chunks = chunker.split(text, 120, 20);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }

    @Test
    void returnsEmptyListForBlankText() {
        assertThat(chunker.split("  \n\n ")).isEmpty();
    }
}
