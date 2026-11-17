package com.campuspedia.rag;

import com.campuspedia.service.HashEmbeddingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HybridRetriever 的 RRF 融合得分单测，以及向量维度契约回归。
 */
class HybridRetrieverTest {

    @Test
    void lowerRankScoresHigher() {
        double rank1 = HybridRetriever.rrfScore(1, null);
        double rank10 = HybridRetriever.rrfScore(10, null);
        assertTrue(rank1 > rank10, "排名靠前得分应更高");
    }

    @Test
    void bothRanksSumUp() {
        double both = HybridRetriever.rrfScore(1, 1);
        double vectorOnly = HybridRetriever.rrfScore(1, null);
        assertTrue(both > vectorOnly, "同时命中两路得分应更高");
    }

    @Test
    void nullRanksZero() {
        assertEquals(0.0, HybridRetriever.rrfScore(null, null), 0.0001);
    }

    /** 回归：默认 hash provider 的维度必须与 sql/init_pgvector.sql 的 vector(1024) 一致。 */
    @Test
    void hashEmbeddingDimensionMatchesPgvectorColumn() {
        HashEmbeddingService svc = new HashEmbeddingService();
        assertEquals(1024, svc.dimension());
        assertEquals(1024, svc.embed("转专业条件").length);
    }
}
