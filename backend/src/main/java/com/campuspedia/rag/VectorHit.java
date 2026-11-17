package com.campuspedia.rag;
/**
 * 向量检索命中结果值对象，隶属 RAG 检索链路层（rag 包）。
 *
 * <p>上游：{@code PgVectorStore.search} 把 pgvector 查询结果封装为本类型；
 * 下游：{@code HybridRetriever} 读取它参与混合检索融合，最终转换为 {@code RetrievedChunk}。
 * 本层仅承载数据，不含检索决策逻辑。</p>
 */

/**
 * 一条向量命中的原始记录，字段与 pgvector {@code vector_store} 查询结果一一对应。
 *
 * @param id           {@code vector_store} 主键，与 {@code DocChunk.vectorId} 对应，用于跨库关联
 * @param content      切片正文
 * @param metadataJson 向量库 metadata 列的原始 JSON 文本，延迟到融合阶段再解析
 * @param similarity   余弦相似度（{@code 1 - 余弦距离}），由 pgvector 在 SQL 中计算
 */
public record VectorHit(String id, String content, String metadataJson, double similarity) {}
