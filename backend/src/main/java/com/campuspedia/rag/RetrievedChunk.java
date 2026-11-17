package com.campuspedia.rag;
/**
 * 检索链路统一的切片结果，隶属 RAG 检索链路层（rag 包）。
 *
 * <p>上游：{@code HybridRetriever} 融合向量/关键词结果后产出，或 {@code RagChain.rerank} 用重排分重建；
 * 下游：{@code RagChain} 用它组装来源列表与用户 Prompt。
 * 该类型把向量库元数据与 MySQL 文档属性合并，供展示与引用编号使用。</p>
 */

/**
 * 检索链路统一的切片结果，合并了向量库元数据与 MySQL 文档属性。
 *
 * @param vectorId   {@code vector_store} 主键，关联 {@code DocChunk.vectorId}
 * @param content    切片正文，用于组装 Prompt 与来源摘要
 * @param documentId 所属文档 id
 * @param fileName   文档名，用于来源展示与引用标题
 * @param category   文档分类 id 的字符串形式
 * @param clauseRef  法规条款引用，非法规文档为空串
 * @param sourceUrl  原文链接，输出前须经协议白名单校验
 * @param score      融合或重排得分，仅用于排序与展示
 */
public record RetrievedChunk(String vectorId, String content, long documentId, String fileName,
                             String category, String clauseRef, String sourceUrl, double score) {}
