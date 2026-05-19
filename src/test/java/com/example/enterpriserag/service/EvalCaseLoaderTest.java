package com.example.enterpriserag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class EvalCaseLoaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsCasesFromJsonResource() {
        EvalCaseLoader loader = new EvalCaseLoader(objectMapper, resource("""
                [
                  {
                    "id": "case-1",
                    "question": "报销发票最晚什么时候提交？",
                    "expectedKeywords": ["30", "发票"],
                    "expectedSources": ["company-policy.md"],
                    "expectedAnswer": "发票需要在 30 天内提交。"
                  }
                ]
                """));

        List<EvalCase> cases = loader.loadCases();

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).id()).isEqualTo("case-1");
        assertThat(cases.get(0).question()).isEqualTo("报销发票最晚什么时候提交？");
        assertThat(cases.get(0).expectedKeywords()).containsExactly("30", "发票");
        assertThat(cases.get(0).expectedSources()).containsExactly("company-policy.md");
        assertThat(cases.get(0).expectedAnswer()).isEqualTo("发票需要在 30 天内提交。");
    }

    @Test
    void rejectsEmptyCaseList() {
        EvalCaseLoader loader = new EvalCaseLoader(objectMapper, resource("[]"));

        assertThatThrownBy(loader::loadCases)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("评测集不能为空");
    }

    @Test
    void rejectsCaseWithoutKeywords() {
        EvalCaseLoader loader = new EvalCaseLoader(objectMapper, resource("""
                [
                  {
                    "id": "case-1",
                    "question": "报销发票最晚什么时候提交？",
                    "expectedKeywords": []
                  }
                ]
                """));

        assertThatThrownBy(loader::loadCases)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("评测用例缺少期望关键词：case-1");
    }

    private static ByteArrayResource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
