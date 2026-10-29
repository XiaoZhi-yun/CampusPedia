package com.campuspedia.rag;

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

@Component
public class RerankService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${campus.rerank.api-key}")
    private String apiKey;

    @Value("${campus.rerank.url}")
    private String url;

    @Value("${campus.rerank.model}")
    private String model;

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
            for (JsonNode r : root.path("output").path("results")) {
                results.add(new RerankResult(r.path("index").asInt(), r.path("relevance_score").asDouble()));
            }
            results.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Rerank 调用失败：" + e.getMessage(), e);
        }
    }
}
