package com.campuspedia.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HybridRetriever 的 RRF 融合得分单测。
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
}
