package com.campuspedia.rag;
/** 覆盖模块：RAG 文本递归切分（RecursiveSplitter）；回归目标：空文本、短文本、超长切片与重叠切分边界。 */

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecursiveSplitter 单元测试：覆盖空文本、短文本、超长切片、重叠切分。
 */
class RecursiveSplitterTest {

    private final RecursiveSplitter splitter = new RecursiveSplitter();

    /**
     * 被测行为：空串、null、纯空白文本返回空切片列表。
     * 前置：三种无有效内容输入，指定 size=100、overlap=20。
     * 期望：三者都不产出切片且不抛异常。
     * 回归：空文本未前置判空导致 NPE 或生成空串 chunk 的缺陷。
     */
    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(splitter.split("", 100, 20).isEmpty());
        assertTrue(splitter.split(null, 100, 20).isEmpty());
        assertTrue(splitter.split("   ", 100, 20).isEmpty());
    }

    /**
     * 被测行为：短于切片长度的文本只产出单个切片。
     * 前置：一句中文问句，size=100 远大于文本长度。
     * 期望：恰好 1 个切片且保留原文，不做无意义拆分。
     * 回归：短文本被按固定步长切碎导致语义破碎的缺陷。
     */
    @Test
    void shortTextReturnsSingleChunk() {
        List<String> chunks = splitter.split("转专业需要满足什么条件？", 100, 20);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("转专业"));
    }

    /**
     * 被测行为：超长文本切成多块且每块不超过给定长度。
     * 前置：40 段中文拼接的长文本，size=80、overlap=0。
     * 期望：产出多块、无空白块、每块长度不超过 80。
     * 回归：单块长度突破上限导致下游 embedding 输入被截断的缺陷。
     */
    @Test
    void longTextSplitsWithinSize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("这是用于测试切片逻辑的一段中文文本。");
        List<String> chunks = splitter.split(sb.toString(), 80, 0);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) {
            assertFalse(c.isBlank());
            // 逐块校验长度上限：只要有一块静默超限，写入向量库时就会被截断或报错，故不能只看块数。
            assertTrue(c.length() <= 80, "切片超长: " + c.length());
        }
    }

    /**
     * 被测行为：带重叠参数切分时各块仍非空。
     * 前置：30 段中文拼接文本，size=50、overlap=10。
     * 期望：产出多块且逐块非空白。
     * 回归：overlap 计算越界产出空块或死循环的缺陷。
     */
    @Test
    void overlapKeepsChunksNonEmpty() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append("国家奖学金申请条件明确规定了成绩要求。");
        List<String> chunks = splitter.split(sb.toString(), 50, 10);
        assertTrue(chunks.size() > 1);
        // 重叠切分最易在块首块尾产出空串，逐块非空白是确认 overlap 边界计算未越界。
        for (String c : chunks) assertFalse(c.isBlank());
    }
}
