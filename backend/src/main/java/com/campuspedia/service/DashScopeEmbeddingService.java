package com.campuspedia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 千问 DashScope 向量化实现（OpenAI 兼容模式）。
 * 当 campus-pedia.embedding.provider=dashscope 时启用，调用 {base-url}/embeddings。
 */
@Component
@ConditionalOnProperty(name = "campus-pedia.embedding.provider", havingValue = "dashscope")
public class DashScopeEmbeddingService implements EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${campus-pedia.embedding.dashscope-api-key}")
    private String apiKey;

    @Value("${campus-pedia.embedding.dashscope-base-url}")
    private String baseUrl;

    @Value("${campus-pedia.embedding.model}")
    private String model;

    /** 实际维度在首次成功调用后确定。 */
    private volatile int dimension = -1;

    @Override
    public float[] embed(String text) {
        try {
            Map<String, String> body = Map.of("model", model, "input", text);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/embeddings", request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("error")) {
                throw new IllegalStateException("DashScope 返回错误: " + root.get("error"));
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new IllegalStateException("DashScope 返回缺少 data 字段");
            }
            JsonNode emb = data.get(0).get("embedding");
            float[] vec = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vec[i] = (float) emb.get(i).asDouble();
            }
            dimension = vec.length;
            return vec;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("DashScope embedding 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimension() {
        return dimension > 0 ? dimension : 1024;
    }
}