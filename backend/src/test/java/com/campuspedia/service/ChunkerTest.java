package com.campuspedia.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunker 单元测试：覆盖条款切分、空文本边界、条款引用保留。
 */
class ChunkerTest {

    private final Chunker chunker = new Chunker();

    @Test
    void chunksByClause() {
        String text = """
                第一章 总则
                第一条 测试条款一。
                第二条 测试条款二。
                第二章 转专业
                第十条 可以申请转专业。
                """;
        List<ChunkText> chunks = chunker.chunk(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).text().contains("第一条"));
    }

    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    void keepsClauseRef() {
        String text = """
                第四章 转专业与转学
                第十二条 学生可以申请转专业。
                """;
        List<ChunkText> chunks = chunker.chunk(text);
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).clauseRef().contains("第四章"));
    }
}
