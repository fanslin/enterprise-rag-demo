package com.example.enterpriserag.controller;

import com.example.enterpriserag.dto.AiHealthResponse;
import com.example.enterpriserag.service.AiHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health/ai")
public class AiHealthController {

    private final AiHealthService aiHealthService;

    public AiHealthController(AiHealthService aiHealthService) {
        this.aiHealthService = aiHealthService;
    }

    @GetMapping
    AiHealthResponse status() {
        return aiHealthService.status();
    }
}
