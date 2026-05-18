package com.example.enterpriserag.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvalServiceTest {

    private final RagChatService ragChatService = mock(RagChatService.class);
    private final EvalService service = new EvalService(ragChatService);

    @Test
    void runRetrievalEvalPassesRetrievalOverridesToEveryCase() {
        when(ragChatService.retrieve(anyString(), eq(6), eq(0.35))).thenReturn(List.of());

        service.runRetrievalEval(6, 0.35);

        verify(ragChatService, times(4)).retrieve(anyString(), eq(6), eq(0.35));
    }
}
