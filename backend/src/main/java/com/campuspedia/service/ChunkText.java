package com.campuspedia.service;

/**
 * 切片器的中间产物：条款引用 + 正文，尚未向量化。
 *
 * @param clauseRef 条款引用
 * @param text      切片正文
 */
public record ChunkText(String clauseRef, String text) {}
