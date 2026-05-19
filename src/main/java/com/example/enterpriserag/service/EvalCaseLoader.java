package com.example.enterpriserag.service;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class EvalCaseLoader {

    private static final TypeReference<List<RawEvalCase>> CASE_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Resource resource;

    public EvalCaseLoader(
            ObjectMapper objectMapper,
            @Value("classpath:eval-cases.json") Resource resource
    ) {
        this.objectMapper = objectMapper;
        this.resource = resource;
    }

    public List<EvalCase> loadCases() {
        List<RawEvalCase> rawCases = readRawCases();
        if (rawCases.isEmpty()) {
            throw new IllegalArgumentException("评测集不能为空");
        }
        return rawCases.stream()
                .map(this::validate)
                .toList();
    }

    private List<RawEvalCase> readRawCases() {
        try {
            return objectMapper.readValue(resource.getInputStream(), CASE_LIST_TYPE);
        } catch (IOException exception) {
            throw new IllegalArgumentException("评测集读取失败");
        }
    }

    private EvalCase validate(RawEvalCase rawCase) {
        String id = normalized(rawCase.id());
        if (id.isBlank()) {
            throw new IllegalArgumentException("评测用例缺少 id");
        }
        String question = normalized(rawCase.question());
        if (question.isBlank()) {
            throw new IllegalArgumentException("评测用例缺少问题：" + id);
        }
        List<String> expectedKeywords = normalizeList(rawCase.expectedKeywords());
        if (expectedKeywords.isEmpty()) {
            throw new IllegalArgumentException("评测用例缺少期望关键词：" + id);
        }
        return new EvalCase(
                id,
                question,
                expectedKeywords,
                normalizeList(rawCase.expectedSources()),
                normalized(rawCase.expectedAnswer())
        );
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(EvalCaseLoader::normalized)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private record RawEvalCase(
            String id,
            String question,
            List<String> expectedKeywords,
            List<String> expectedSources,
            String expectedAnswer
    ) {
    }
}
