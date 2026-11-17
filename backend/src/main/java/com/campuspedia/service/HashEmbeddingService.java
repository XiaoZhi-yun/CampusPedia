package com.campuspedia.service;
/**
 * 本地哈希向量化实现，隶属业务服务层（service 包），是 {@code EmbeddingService} 的无密钥默认实现。
 *
 * <p>上游：{@code KnowledgeService} 与 {@code HybridRetriever} 经接口注入；当
 * {@code campus.embedding.provider} 未配置或为 {@code hash} 时由 Spring 条件装配启用。
 * 目的：无外部 Key 的离线/测试环境仍能跑通上传、切片、向量化、检索全链路，生产应切到千问实现。</p>
 */

import org.springframework.beans.factory.annotation.Value;
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

    /** 维度必须与 sql/init_pgvector.sql 的 vector(1024) 列定义一致，否则写入会被 pgvector 拒绝。 */
    @Value("${campus.embedding.dimensions:1024}")
    private int dimension = 1024;

    /**
     * 返回向量维度，供上游与 pgvector 列维度对齐校验。
     *
     * @return {@code campus.embedding.dimensions} 注入的维度，缺省 1024
     */
    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 把文本编码为固定维度向量：字符一元/二元组哈希后做 L2 归一化。
     *
     * <p>纯本地计算、无远程调用与副作用，也不抛受检异常；归一化保证 pgvector 余弦距离计算稳定。</p>
     *
     * @param text 待编码文本；{@code null} 按空串处理
     * @return 长度为 {@code dimension()} 的归一化向量；空文本为全零向量
     */
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
