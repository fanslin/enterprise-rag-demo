package com.example.enterpriserag.controller;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void badRequestKeepsValidationMessage() throws Exception {
        mockMvc.perform(get("/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("问题不能为空"));
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalDetails() throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("服务暂时不可用，请稍后重试或检查模型服务配置。"))
                .andExpect(jsonPath("$.message", not("secret-api-key")));
    }

    @Test
    void missingStaticResourceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/missing-static-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    @RestController
    private static class ThrowingController {

        @GetMapping("/bad-request")
        String badRequest() {
            throw new IllegalArgumentException("问题不能为空");
        }

        @GetMapping("/unexpected")
        String unexpected() {
            throw new IllegalStateException("secret-api-key");
        }

        @GetMapping("/missing-static-resource")
        String missingStaticResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "favicon.ico");
        }
    }
}
