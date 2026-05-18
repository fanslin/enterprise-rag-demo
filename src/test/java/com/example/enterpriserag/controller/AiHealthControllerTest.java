package com.example.enterpriserag.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.enterpriserag.EnterpriseRagDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = EnterpriseRagDemoApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.ai.openai.chat.api-key=",
        "spring.ai.openai.embedding.api-key="
})
class AiHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void localProfileStartsWithoutModelApiKeysAndReportsMockMode() throws Exception {
        mockMvc.perform(get("/api/health/ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("local"))
                .andExpect(jsonPath("$.chatConfigured").value(true))
                .andExpect(jsonPath("$.embeddingConfigured").value(true))
                .andExpect(jsonPath("$.message").value("本地 Mock 模式已启用，不会调用外部模型服务。"));
    }
}
