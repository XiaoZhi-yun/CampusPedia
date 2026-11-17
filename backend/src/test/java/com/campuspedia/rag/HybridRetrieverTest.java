package com.campuspedia.rag;
/** 覆盖模块：混合检索 RRF 融合得分与向量维度契约；回归目标：排名权重、双路命中叠加、1024 维一致性。 */

import com.campuspedia.service.HashEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HybridRetriever 的 RRF 融合得分单测，以及向量维度契约回归。
 */
class HybridRetrieverTest {

    /**
     * 被测行为：RRF 融合得分随排名靠前而升高。
     * 前置：仅向量路命中，分别取 rank=1 与 rank=10，另一路为 null。
     * 期望：rank=1 得分严格大于 rank=10。
     * 回归：排名权重反置导致高相关结果被排到后面的缺陷。
     */
    @Test
    void lowerRankScoresHigher() {
        double rank1 = HybridRetriever.rrfScore(1, null);
        double rank10 = HybridRetriever.rrfScore(10, null);
        assertTrue(rank1 > rank10, "排名靠前得分应更高");
    }

    /**
     * 被测行为：同时命中向量路与关键词路时得分叠加。
     * 前置：两路同为 rank=1，对照仅向量路命中、关键词路为 null。
     * 期望：双路命中的得分高于单路命中。
     * 回归：只取单路排名、忽略融合加和导致多路召回优势丢失的缺陷。
     */
    @Test
    void bothRanksSumUp() {
        double both = HybridRetriever.rrfScore(1, 1);
        double vectorOnly = HybridRetriever.rrfScore(1, null);
        assertTrue(both > vectorOnly, "同时命中两路得分应更高");
    }

    /**
     * 被测行为：两路排名均为 null 时得分为 0。
     * 前置：rrfScore(null, null)，以 0.0001 容差比较浮点。
     * 期望：返回 0.0，不产生 NaN 或异常。
     * 回归：null 排名参与倒数运算产生 NaN 并污染整体排序的缺陷。
     */
    @Test
    void nullRanksZero() {
        assertEquals(0.0, HybridRetriever.rrfScore(null, null), 0.0001);
    }

    // 前置：直接 new HashEmbeddingService，不依赖外部模型服务；期望：声明值与实际 embed 长度均为 1024。
    /** 回归：默认 hash provider 的维度必须与 sql/init_pgvector.sql 的 vector(1024) 一致。 */
    @Test
    void hashEmbeddingDimensionMatchesPgvectorColumn() {
        HashEmbeddingService svc = new HashEmbeddingService();
        assertEquals(1024, svc.dimension());
        // 仅 dimension 对齐不够：实际 embed 输出长度也要是 1024，否则写入 vector(1024) 列时才发现不匹配。
        assertEquals(1024, svc.embed("转专业条件").length);
    }
}
