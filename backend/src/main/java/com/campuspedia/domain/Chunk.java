package com.campuspedia.domain;

/**
 * 切片：检索与溯源的最小单元，承载原文片段、元数据与向量。
 *
 * @param id         切片唯一标识
 * @param documentId 所属文档 id
 * @param fileName   来源文件名（用于溯源引用展示）
 * @param category   所属类目
 * @param clauseRef  条款引用（如"第四章 转专业与转学"），是回答时标注 [来源N] 的依据
 * @param text       切片正文
 * @param embedding  切片向量（用于相似度检索）
 */
public record Chunk(
        String id,
        String documentId,
        String fileName,
        String category,
        String clauseRef,
        String text,
        float[] embedding
) {}
