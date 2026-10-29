package com.campuspedia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM 调用（OpenAI 兼容 chat/completions，非流式）。
 */
@Component
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${campus-pedia.llm.deepseek-api-key}")
    private String apiKey;

    @Value("${campus-pedia.llm.deepseek-base-url}")
    private String baseUrl;

    @Value("${campus-pedia.llm.model}")
    private String model;

    /**
     * 调用 DeepSeek 生成答案。
     *
     * @param system 系统提示词
     * @param user   用户消息（含参考资料与问题）
     * @return 生成文本
     */
    public String chat(String system, String user) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "temperature", 0.3,
                    "stream", false
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                throw new IllegalStateException("DeepSeek 返回错误: " + root.get("error"));
            }
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek 调用失败: " + e.getMessage(), e);
        }
    }
}