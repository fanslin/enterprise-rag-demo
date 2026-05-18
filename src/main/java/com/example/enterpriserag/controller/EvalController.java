package com.example.enterpriserag.controller;

import java.util.List;

import com.example.enterpriserag.dto.EvalCaseResult;
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
    List<EvalCaseResult> run(
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Double similarityThreshold
    ) {
        return evalService.runRetrievalEval(topK, similarityThreshold);
    }
}
