package com.campuspedia.service;

/**
 * 向量化服务抽象：文本 → 向量。
 *
 * <p>当前提供本地哈希实现（{@code HashEmbeddingService}，无 Key 时跑通链路），
 * 后续接入千问 DashScope text-embedding-v3 时新增实现类即可，上层不感知。</p>
 */
public interface EmbeddingService {

    /**
     * 将文本编码为向量。
     *
     * @param text 待编码文本
     * @return 归一化后的向量
     */
    float[] embed(String text);

    /**
     * 向量维度。
     *
     * @return 维度大小
     */
    int dimension();
}
