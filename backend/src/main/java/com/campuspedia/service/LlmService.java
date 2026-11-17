package com.campuspedia.service;
/**
 * DeepSeek 大模型调用封装，隶属业务服务层（service 包），提供非流式与流式两种 chat/completions 调用。
 *
 * <p>上游：{@code QueryRewriter}（多轮改写走非流式）、{@code RagChain}（答案生成走流式）；
 * 下游：DeepSeek OpenAI 兼容 HTTP 接口。本层只负责协议编解码与异常包装，不参与 Prompt 组装与检索。</p>
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek LLM 调用：非流式 chat 与流式 stream（OpenAI 兼容 chat/completions）。
 */
@Component
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** DeepSeek API Key；缺失时 chat/stream 会鉴权失败，本层不做降级。 */
    @Value("${campus.llm.deepseek-api-key}")
    private String apiKey;

    /** OpenAI 兼容服务根地址；chat 与 stream 均在其后拼接 /chat/completions。 */
    @Value("${campus.llm.deepseek-base-url}")
    private String baseUrl;

    /** 模型名；查询改写与答案生成共用同一模型配置。 */
    @Value("${campus.llm.model}")
    private String model;

    /** 非流式生成。 */
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

    /**
     * 流式生成：逐段回调 delta 内容（解析 SSE 的 data 行，遇 [DONE] 结束）。
     */
    public void stream(String system, String user, Consumer<String> onDelta) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    ),
                    "temperature", 0.3,
                    "stream", true
            );
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE 仅 data: 行承载增量内容，event/id/空行/心跳等一律跳过
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    // [DONE] 是 OpenAI 兼容流的结束哨兵，收到即停止读取
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull()) {
                        onDelta.accept(delta.asText());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek 流式调用失败: " + e.getMessage(), e);
        }
    }
}