package com.campuspedia.domain;

import java.time.Instant;

/**
 * 文档元信息：一次上传产生一个文档，对应若干切片（{@link Chunk}）。
 *
 * @param id         文档唯一标识（UUID）
 * @param fileName   原始文件名
 * @param category   所属类目
 * @param sourceUrl  官网原文链接（可空）
 * @param charCount  原文字符数
 * @param chunkCount 切分出的切片数量
 * @param createdAt  上传时间
 */
public record Document(
        String id,
        String fileName,
        String category,
        String sourceUrl,
        int charCount,
        int chunkCount,
        Instant createdAt
) {}
