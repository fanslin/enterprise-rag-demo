package com.example.enterpriserag.controller;

import com.example.enterpriserag.dto.ChatRequest;
import com.example.enterpriserag.dto.ChatResponse;
import com.example.enterpriserag.service.RagChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping
    ChatResponse ask(@RequestBody ChatRequest request) {
        return ragChatService.ask(request.question(), request.topK(), request.similarityThreshold());
    }
}
