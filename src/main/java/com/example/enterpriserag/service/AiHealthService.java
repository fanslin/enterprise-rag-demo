package com.example.enterpriserag.service;

import java.util.Arrays;

import com.example.enterpriserag.dto.AiHealthResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiHealthService {

    private final Environment environment;

    public AiHealthService(Environment environment) {
        this.environment = environment;
    }

    public AiHealthResponse status() {
        if (isLocalProfile()) {
            return new AiHealthResponse(
                    "local",
                    true,
                    true,
                    "本地 Mock 模式已启用，不会调用外部模型服务。"
            );
        }

        boolean chatConfigured = hasText("spring.ai.openai.chat.api-key");
        boolean embeddingConfigured = hasText("spring.ai.openai.embedding.api-key");
        String message = chatConfigured && embeddingConfigured
                ? "真实模型服务已配置。"
                : "真实模型服务未完整配置，请设置 GROQ_API_KEY 和 ZAI_API_KEY，或使用 local profile。";

        return new AiHealthResponse("remote", chatConfigured, embeddingConfigured, message);
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    private boolean hasText(String propertyName) {
        return StringUtils.hasText(environment.getProperty(propertyName));
    }
}
