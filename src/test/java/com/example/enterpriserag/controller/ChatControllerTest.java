package com.example.enterpriserag.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.enterpriserag.dto.ChatResponse;
import com.example.enterpriserag.service.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatControllerTest {

    private final RagChatService ragChatService = mock(RagChatService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatController(ragChatService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void askPassesRetrievalOverridesFromRequestBody() throws Exception {
        when(ragChatService.ask(eq("报销规则"), eq(8), eq(0.42)))
                .thenReturn(new ChatResponse("回答", List.of()));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "报销规则",
                                  "topK": 8,
                                  "similarityThreshold": 0.42
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("回答"));

        verify(ragChatService).ask("报销规则", 8, 0.42);
    }
}
