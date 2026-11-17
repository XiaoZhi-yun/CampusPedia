package com.campuspedia.rag;
/**
 * 重排打分结果值对象，隶属 RAG 检索链路层（rag 包）。
 *
 * <p>上游：{@code RerankService} 解析 DashScope rerank 响应产出；
 * 下游：{@code RagChain.rerank} 按 {@code relevanceScore} 过滤并回溯候选下标，组装最终命中。
 * {@code index} 是候选列表下标，不是向量库主键。</p>
 */

/**
 * 一条候选的重排打分。
 *
 * @param index          对应送入 rerank 的候选列表下标，供上游回溯原始切片
 * @param relevanceScore 相关性得分，值越大越相关；上游按 {@code rag_score_threshold} 过滤
 */
public record RerankResult(int index, double relevanceScore) {}
