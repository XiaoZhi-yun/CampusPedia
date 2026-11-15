package com.campuspedia.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RecursiveSplitter 单元测试：覆盖空文本、短文本、超长切片、重叠切分。
 */
class RecursiveSplitterTest {

    private final RecursiveSplitter splitter = new RecursiveSplitter();

    @Test
    void emptyTextReturnsEmpty() {
        assertTrue(splitter.split("", 100, 20).isEmpty());
        assertTrue(splitter.split(null, 100, 20).isEmpty());
        assertTrue(splitter.split("   ", 100, 20).isEmpty());
    }

    @Test
    void shortTextReturnsSingleChunk() {
        List<String> chunks = splitter.split("转专业需要满足什么条件？", 100, 20);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("转专业"));
    }

    @Test
    void longTextSplitsWithinSize() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append("这是用于测试切片逻辑的一段中文文本。");
        List<String> chunks = splitter.split(sb.toString(), 80, 0);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) {
            assertFalse(c.isBlank());
            assertTrue(c.length() <= 80, "切片超长: " + c.length());
        }
    }

    @Test
    void overlapKeepsChunksNonEmpty() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append("国家奖学金申请条件明确规定了成绩要求。");
        List<String> chunks = splitter.split(sb.toString(), 50, 10);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) assertFalse(c.isBlank());
    }
}
