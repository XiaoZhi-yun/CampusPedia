package com.campuspedia.service;

import com.campuspedia.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrieverTest {

    private Chunk chunk(String id, float[] emb) {
        return new Chunk(id, "doc1", "test.md", "测试", "第一条", "正文", emb);
    }

    @Test
    void filtersByThresholdAndSortsBySimilarity() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        VectorStore store = mock(VectorStore.class);
        when(embedding.embed("查询")).thenReturn(new float[]{1, 0});
        when(store.getAll()).thenReturn(List.of(
                chunk("low", new float[]{0, 1}),   // 相似度 0
                chunk("high", new float[]{1, 0})   // 相似度 1
        ));

        Retriever retriever = new Retriever(store, embedding);
        List<RetrievalResult> res = retriever.retrieve("查询", 6, 0.5);

        assertEquals(1, res.size());
        assertEquals("high", res.get(0).chunk().id());
    }

    @Test
    void returnsEmptyWhenNothingAboveThreshold() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        VectorStore store = mock(VectorStore.class);
        when(embedding.embed("查询")).thenReturn(new float[]{1, 0});
        when(store.getAll()).thenReturn(List.of(chunk("low", new float[]{0, 1})));

        Retriever retriever = new Retriever(store, embedding);
        assertTrue(retriever.retrieve("查询", 6, 0.5).isEmpty());
    }
}
