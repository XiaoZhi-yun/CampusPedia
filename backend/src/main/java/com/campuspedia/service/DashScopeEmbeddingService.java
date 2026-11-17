package com.campuspedia.service;
/**
 * 千问 DashScope 向量化实现，隶属业务服务层（service 包），是 {@code EmbeddingService} 的在线实现。
 *
 * <p>上游：同 {@code HashEmbeddingService}，由 {@code KnowledgeService}/{@code HybridRetriever} 经接口注入；
 * 仅当 {@code campus.embedding.provider=dashscope} 时条件装配。下游：DashScope OpenAI 兼容 embeddings 接口。
 * 契约：返回向量维度须与 pgvector 的 {@code vector(1024)} 列一致。</p>
 */

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
 * 当 campus.embedding.provider=dashscope 时启用，调用 {base-url}/embeddings。
 */
@Component
@ConditionalOnProperty(name = "campus.embedding.provider", havingValue = "dashscope")
public class DashScopeEmbeddingService implements EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${campus.embedding.api-key}")
    private String apiKey;

    @Value("${campus.embedding.base-url}")
    private String baseUrl;

    @Value("${campus.embedding.model}")
    private String model;

    /** 配置声明维度（默认 1024）：首次成功调用前作为 {@code dimension()} 的兜底，须与 pgvector {@code vector(1024)} 一致。 */
    @Value("${campus.embedding.dimensions:1024}")
    private int configuredDimension = 1024;

    /** 实际维度在首次成功调用后确定。 */
    private volatile int dimension = -1;

    /**
     * 调用 DashScope embeddings 接口把文本编码为向量，并在首次成功后据返回长度校准维度。
     *
     * @param text 待编码文本
     * @return 接口返回的浮点向量
     * @throws IllegalStateException 响应带 error 或缺少 data 字段时抛出
     * @throws RuntimeException      HTTP/JSON 等异常统一包装为非受检，是否降级由上游决定
     */
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

    /**
     * 返回向量维度。
     *
     * @return 首次成功调用后为接口返回的真实维度，否则回退到 {@code configuredDimension}
     */
    @Override
    public int dimension() {
        return dimension > 0 ? dimension : configuredDimension;
    }
}