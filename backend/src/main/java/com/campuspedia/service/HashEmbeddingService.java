package com.campuspedia.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地哈希向量化实现：字符一元/二元组哈希后做 L2 归一化。
 *
 * <p>用途：在未配置千问 Key 时让"上传→切片→向量化"链路可跑通、可测试；
 * 共享字符越多相似度越高，具备一定的语义区分能力。生产环境应切换到千问 DashScope。</p>
 */
@Component
@ConditionalOnProperty(name = "campus.embedding.provider", havingValue = "hash", matchIfMissing = true)
public class HashEmbeddingService implements EmbeddingService {

    /** 向量维度（与后续千问 embedding 维度解耦，通过接口约束）。 */
    private final int dimension = 128;

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimension];
        String t = text == null ? "" : text;
        for (int i = 0; i < t.length(); i++) {
            int c = t.charAt(i);
            // 一元组：单字符散列到维度桶
            v[Math.floorMod(c, dimension)] += 1.0f;
            if (i + 1 < t.length()) {
                // 二元组：相邻字符组合散列，捕获局部词序
                int bi = Math.floorMod(c * 31 + t.charAt(i + 1) * 131, dimension);
                v[bi] += 0.5f;
            }
        }
        // L2 归一化，保证余弦相似度计算稳定
        double norm = 0;
        for (float x : v) {
            norm += (double) x * x;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) (v[i] / norm);
            }
        }
        return v;
    }
}
