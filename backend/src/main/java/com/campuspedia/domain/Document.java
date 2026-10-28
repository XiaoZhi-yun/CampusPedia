package com.campuspedia.domain;

import java.time.Instant;

/**
 * 文档元信息：一次上传产生一个文档，对应若干切片（{@link Chunk}）。
 * 用于管理端"文档列表"展示与溯源归属。
 *
 * @param id         文档唯一标识（UUID）
 * @param fileName   原始文件名
 * @param category   所属类目（学籍与教学管理 / 学生管理 / 学生奖助 / 素质拓展 / 学生就业 / 医疗保障）
 * @param charCount  原文字符数
 * @param chunkCount 切分出的切片数量
 * @param createdAt  上传时间
 */
public record Document(
        String id,
        String fileName,
        String category,
        int charCount,
        int chunkCount,
        Instant createdAt
) {}
