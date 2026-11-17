package com.campuspedia.service;
/**
 * 切片中间产物，隶属业务服务层（service 包），在 {@code Chunker} 与 {@code KnowledgeService} 之间传递。
 *
 * <p>上游：{@code Chunker.chunk} 产出；下游：{@code KnowledgeService.vectorize} 拆出正文与条款引用，
 * 分别送入向量化与切片落库。此对象尚未向量化，仅在入库前存在。</p>
 */

/**
 * 切片器的中间产物：条款引用 + 正文，尚未向量化。
 *
 * @param clauseRef 条款引用
 * @param text      切片正文
 */
public record ChunkText(String clauseRef, String text) {}
