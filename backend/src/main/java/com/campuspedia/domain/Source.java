package com.campuspedia.domain;

/**
 * 溯源引用：回答中 [来源N] 对应的元信息。
 *
 * @param index      来源编号（从 1 开始，对应 [来源N]）
 * @param fileName   来源文件名
 * @param category   所属类目
 * @param clauseRef  条款引用（第X章/第X条）
 * @param snippet    原文片段（截断）
 * @param similarity 与问题的相似度
 * @param sourceUrl  官网原文链接（可空）
 */
public record Source(
        int index,
        String fileName,
        String category,
        String clauseRef,
        String snippet,
        double similarity,
        String sourceUrl
) {}
