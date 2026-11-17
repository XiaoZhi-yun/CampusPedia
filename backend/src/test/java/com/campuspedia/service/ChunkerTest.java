package com.campuspedia.service;
/** 覆盖模块：规章制度/法规文本切分（Chunker）；回归目标：条款边界、空文本、条款引用保留不退化。 */

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunker 单元测试：覆盖条款切分、空文本边界、条款引用保留。
 */
class ChunkerTest {

    private final Chunker chunker = new Chunker();

    /**
     * 被测行为：把含章、条结构的法规文本按条款切块。
     * 前置：含第一章/第一条/第二条/第二章/第十条的多条款文本，chunker 为无状态新实例。
     * 期望：产出非空分块且首块保留第一条内容，避免整章被塞进同一块。
     * 回归：条款边界未被识别导致检索粒度退化为整章的缺陷。
     */
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
        // 首块必须落在具体条款文本：若未以第X条断开，整章会被塞进单块，检索粒度随之失准。
        assertTrue(chunks.get(0).text().contains("第一条"));
    }

    /**
     * 被测行为：空串、null、纯空白文本切分后返回空列表。
     * 前置：三种无有效内容输入，chunker 无外部依赖。
     * 期望：三者都不产出分块且不抛异常。
     * 回归：空文本未前置判空导致 NPE 或生成空 chunk 污染向量库的缺陷。
     */
    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    /**
     * 被测行为：切块时保留所属章节引用 clauseRef。
     * 前置：第四章 转专业与转学 / 第十二条 文本。
     * 期望：首块 clauseRef 含第四章，供检索结果回溯原文位置。
     * 回归：切分后丢失章节归属导致引用无法定位的缺陷。
     */
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
