package com.campuspedia.service;

import com.campuspedia.domain.Chunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索服务：问题向量化后与全库切片做余弦相似度，按阈值过滤、相似度降序，取 Top-K。
 */
@Component
public class Retriever {

    private final VectorStore vectorStore;
    private final EmbeddingService embedding;

    public Retriever(VectorStore vectorStore, EmbeddingService embedding) {
        this.vectorStore = vectorStore;
        this.embedding = embedding;
    }

    /**
     * 检索最相关的切片。
     *
     * @param query     用户问题
     * @param topK      返回条数上限
     * @param threshold 相似度阈值（低于则不相关）
     * @return 按相似度降序的检索结果
     */
    public List<RetrievalResult> retrieve(String query, int topK, double threshold) {
        float[] queryVec = embedding.embed(query);
        List<RetrievalResult> results = new ArrayList<>();
        for (Chunk chunk : vectorStore.getAll()) {
            double sim = cosine(queryVec, chunk.embedding());
            if (sim >= threshold) {
                results.add(new RetrievalResult(chunk, sim));
            }
        }
        results.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        int end = Math.min(topK, results.size());
        return new ArrayList<>(results.subList(0, end));
    }

    /** 余弦相似度（兼容归一化/未归一化向量）。 */
    private double cosine(float[] a, float[] b) {
        if (a == null || b == null) {
            return 0;
        }
        int n = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
