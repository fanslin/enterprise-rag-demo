package com.example.enterpriserag.controller;

import java.util.List;

import com.example.enterpriserag.dto.EvalReport;
import com.example.enterpriserag.service.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping
    EvalReport run(
            @RequestParam(required = false) String topK,
            @RequestParam(required = false) String similarityThreshold
    ) {
        return evalService.runRetrievalEval(
                parseTopKValues(topK),
                parseSimilarityThresholdValues(similarityThreshold)
        );
    }

    private static List<Integer> parseTopKValues(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return split(rawValue).stream()
                .map(EvalController::parseTopK)
                .toList();
    }

    private static List<Double> parseSimilarityThresholdValues(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        return split(rawValue).stream()
                .map(EvalController::parseSimilarityThreshold)
                .toList();
    }

    private static List<String> split(String rawValue) {
        return List.of(rawValue.split(",")).stream()
                .map(String::trim)
                .peek(value -> {
                    if (value.isBlank()) {
                        throw new IllegalArgumentException("参数格式错误：" + rawValue);
                    }
                })
                .toList();
    }

    private static int parseTopK(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("topK 参数格式错误：" + value);
        }
        if (parsed < 1 || parsed > 20) {
            throw new IllegalArgumentException("topK 必须在 1 到 20 之间");
        }
        return parsed;
    }

    private static double parseSimilarityThreshold(String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("similarityThreshold 参数格式错误：" + value);
        }
        if (parsed < 0.0 || parsed > 1.0) {
            throw new IllegalArgumentException("similarityThreshold 必须在 0.0 到 1.0 之间");
        }
        return parsed;
    }
}
