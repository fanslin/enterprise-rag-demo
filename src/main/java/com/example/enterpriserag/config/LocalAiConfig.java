package com.example.enterpriserag.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalAiConfig {

    @Bean
    ChatModel localChatModel() {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("""
                本地 Mock 回答：当前运行在 local profile，不会调用外部模型服务。
                请使用真实 API Key 运行默认 profile 以获得模型生成回答。

                引用：local-mock#0
                """))));
    }

    @Bean
    EmbeddingModel localEmbeddingModel() {
        return new LocalEmbeddingModel();
    }

    private static final class LocalEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 32;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(vectorize(instructions.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vectorize(getEmbeddingContent(document));
        }

        @Override
        public int dimensions() {
            return DIMENSIONS;
        }

        private static float[] vectorize(String text) {
            float[] vector = new float[DIMENSIONS];
            byte[] bytes = String.valueOf(text).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < bytes.length; i++) {
                int bucket = i % DIMENSIONS;
                vector[bucket] += (bytes[i] & 0xff) / 255.0f;
            }
            normalize(vector);
            return vector;
        }

        private static void normalize(float[] vector) {
            double sum = 0.0;
            for (float value : vector) {
                sum += value * value;
            }
            if (sum == 0.0) {
                vector[0] = 1.0f;
                return;
            }
            float length = (float) Math.sqrt(sum);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / length;
            }
        }
    }
}
