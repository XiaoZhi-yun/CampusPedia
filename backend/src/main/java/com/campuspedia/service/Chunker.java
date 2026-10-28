package com.campuspedia.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条款级切片器：按"第X章 / 第X条"边界切分，尽量保持条款完整，避免把一条规则切成两段。
 *
 * <p>切分策略：</p>
 * <ul>
 *   <li>软上限 SOFT_LIMIT：达到 400 字且遇到下一个条款头时切分；</li>
 *   <li>硬上限 HARD_LIMIT：达到 600 字强制切分，防止超长块；</li>
 *   <li>每个块的 clauseRef 固定为该块的起始条款，不随后续条款覆盖。</li>
 * </ul>
 */
@Component
public class Chunker {

    /** 条款头：匹配"第X章 / 第X条"开头的行（支持中文数字与阿拉伯数字）。 */
    private static final Pattern CLAUSE_HEADER =
            Pattern.compile("^\\s*(第[一二三四五六七八九十百零〇\\d]+[章条].*)$");

    /** 软上限：达到后优先在条款边界切分。 */
    private static final int SOFT_LIMIT = 400;

    /** 硬上限：超过后强制切分，防止单个切片过长。 */
    private static final int HARD_LIMIT = 600;

    /**
     * 将整篇文本切分为若干切片。
     *
     * @param text 原文（Markdown / 纯文本）
     * @return 切片列表；空文本返回空列表
     */
    public List<ChunkText> chunk(String text) {
        List<ChunkText> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");

        StringBuilder buf = new StringBuilder();
        String bufRef = "";
        for (String line : lines) {
            String trimmed = line.trim();
            boolean isHeader = CLAUSE_HEADER.matcher(trimmed).matches();

            // 达到软上限且遇到条款边界：切分当前块
            if (buf.length() >= SOFT_LIMIT && isHeader) {
                flush(chunks, buf, bufRef);
                bufRef = "";
            }
            // 记录当前块的起始条款（仅当块内还没有条款头时）
            if (isHeader && bufRef.isEmpty()) {
                bufRef = trimmed;
            }
            buf.append(line).append('\n');

            // 硬上限：强制切分，防止超长块
            if (buf.length() >= HARD_LIMIT) {
                flush(chunks, buf, bufRef);
                bufRef = "";
            }
        }
        if (!buf.toString().isBlank()) {
            flush(chunks, buf, bufRef);
        }
        return chunks;
    }

    /** 将当前缓冲区落盘为一个切片，并清空缓冲区。 */
    private void flush(List<ChunkText> chunks, StringBuilder buf, String ref) {
        String t = buf.toString().trim();
        if (!t.isEmpty()) {
            chunks.add(new ChunkText(ref, t));
        }
        buf.setLength(0);
    }
}
