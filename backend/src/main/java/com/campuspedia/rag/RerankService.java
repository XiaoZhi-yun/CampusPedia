package com.campuspedia.rag;
/**
 * 候选重排服务，隶属 RAG 检索链路层（rag 包），调用 DashScope rerank 接口为候选打分。
 *
 * <p>上游：{@code RagChain.rerank} 在混合检索后调用；下游：DashScope rerank HTTP 接口。
 * 本类只负责打分并按分数降序返回，是否启用、失败降级（退回纯混合检索结果）由上游 {@code RagChain} 决定。</p>
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 query 与候选文档列表交给 rerank 模型，返回按相关性降序的打分结果。
 *
 * <p>本类对调用失败不做降级、统一包装为 {@code RuntimeException} 上抛，
 * 由 {@code RagChain} 捕获后退回纯混合检索结果，保证重排不可用时问答仍可用。</p>
 */
@Component
public class RerankService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** rerank 服务密钥；缺失或失效时调用失败，由上游 RagChain 降级为纯检索。 */
    @Value("${campus.rerank.api-key}")
    private String apiKey;

    /** rerank 端点地址（独立于 LLM base-url），指向 DashScope 重排服务。 */
    @Value("${campus.rerank.url}")
    private String url;

    /** rerank 模型名，与 query、documents 一起构成请求体。 */
    @Value("${campus.rerank.model}")
    private String model;

    /**
     * 对候选文档按与 query 的相关性打分并降序返回。
     *
     * @param query     用户原始问题（贴合真实意图，不用改写后的问题）
     * @param documents 候选正文列表，其下标即返回结果中的 {@code index}
     * @return 按 {@code relevanceScore} 降序的打分列表
     * @throws RuntimeException 网络、鉴权或响应解析失败时统一包装抛出，交由上游降级
     */
    public List<RerankResult> rerank(String query, List<String> documents) {
        try {
            Map<String, Object> input = Map.of("query", query, "documents", documents);
            Map<String, Object> body = Map.of("model", model, "input", input);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            List<RerankResult> results = new ArrayList<>();
            // 响应按 output.results 数组给出 index/relevance_score，index 回指候选下标
            for (JsonNode r : root.path("output").path("results")) {
                results.add(new RerankResult(r.path("index").asInt(), r.path("relevance_score").asDouble()));
            }
            results.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));
            return results;
        } catch (Exception e) {
            // 不在本层吞掉异常：交给 RagChain 决定降级，避免静默返回错序结果
            throw new RuntimeException("Rerank 调用失败：" + e.getMessage(), e);
        }
    }
}