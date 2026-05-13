package com.example.enterpriserag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {

    private static final int DEFAULT_CHUNK_SIZE = 900;
    private static final int DEFAULT_OVERLAP = 120;

    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    List<String> split(String text, int chunkSize, int overlap) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());
            end = findReadableBreak(normalized, start, end);

            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static int findReadableBreak(String text, int start, int hardEnd) {
        if (hardEnd == text.length()) {
            return hardEnd;
        }

        int paragraphBreak = text.lastIndexOf("\n\n", hardEnd);
        if (paragraphBreak > start + 200) {
            return paragraphBreak + 2;
        }

        int sentenceBreak = Math.max(text.lastIndexOf("。", hardEnd), text.lastIndexOf(".", hardEnd));
        if (sentenceBreak > start + 200) {
            return sentenceBreak + 1;
        }

        int lineBreak = text.lastIndexOf("\n", hardEnd);
        if (lineBreak > start + 200) {
            return lineBreak + 1;
        }

        return hardEnd;
    }
}
