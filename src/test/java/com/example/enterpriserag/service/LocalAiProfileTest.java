package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.enterpriserag.EnterpriseRagDemoApplication;
import com.example.enterpriserag.dto.ChatResponse;
import com.example.enterpriserag.dto.IngestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = EnterpriseRagDemoApplication.class)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.ai.openai.chat.api-key=",
        "spring.ai.openai.embedding.api-key="
})
class LocalAiProfileTest {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private RagChatService ragChatService;

    @Test
    void localProfileCanIngestAndAnswerWithoutExternalModelServices() {
        IngestResponse ingest = knowledgeBaseService.ingest(
                "local-policy.md",
                "报销发票需要在 30 天内提交。远程办公需要提前 1 天申请。"
        );

        ChatResponse response = ragChatService.ask("报销发票最晚什么时候提交？");

        assertThat(ingest.chunks()).isEqualTo(1);
        assertThat(response.answer()).contains("本地 Mock 回答");
        assertThat(response.citations()).hasSize(1);
    }
}
