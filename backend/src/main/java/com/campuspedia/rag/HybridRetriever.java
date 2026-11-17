package com.campuspedia.rag;
/**
 * 混合检索器，隶属 RAG 检索链路层（rag 包），融合 pgvector 向量检索与 MySQL ngram 全文检索。
 *
 * <p>上游：{@code RagChain.stream} 用改写后的问题召回候选；下游：{@code PgVectorStore}（向量）、
 * {@code DocChunkMapper.fullTextSearch}（关键词）、{@code EmbeddingService}（问题编码）、
 * {@code KbDocumentMapper}（补全文档属性）。两路检索各自失败都记 warn 并降级，最终用 RRF 融合排序。</p>
 */

import com.campuspedia.entity.DocChunk;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.mapper.DocChunkMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import com.campuspedia.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 取向量与关键词两路召回，用 RRF（倒数排名融合）合并名次后统一排序。
 *
 * <p>两路互补：向量路抓语义相近、关键词路抓精确措辞（如条款号）。任一路失败只记 warn
 * 并以另一路结果继续，避免单点故障导致零召回；RRF 只依赖名次、无需对齐两路分数尺度。</p>
 */
@Component
public class HybridRetriever {
    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final PgVectorStore vectorStore;
    private final EmbeddingService embedding;
    private final DocChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param vectorStore    pgvector 向量检索
     * @param embedding      问题向量化
     * @param chunkMapper    MySQL 切片全文检索
     * @param documentMapper 补全文档名、分类、来源链接等属性
     */
    public HybridRetriever(PgVectorStore vectorStore, EmbeddingService embedding,
                           DocChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
        this.vectorStore = vectorStore;
        this.embedding = embedding;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * 召回并融合候选切片。
     *
     * @param query         检索问题（通常为改写后的问题）
     * @param topKRecall    每路召回条数上限
     * @param hybridEnabled 是否启用关键词路；关闭时退化为纯向量检索
     * @return 按 RRF 融合分降序的候选列表；两路均失败时为空列表
     */
    public List<RetrievedChunk> retrieve(String query, int topKRecall, boolean hybridEnabled) {
        Map<String, Integer> vectorRank = new LinkedHashMap<>();
        Map<String, RetrievedChunk> metaById = new LinkedHashMap<>();
        try {
            // 问题向量化 + pgvector 近邻检索整体兜在 try 内，异常时降级、不影响关键词路
            float[] qv = embedding.embed(query);
            List<VectorHit> hits = vectorStore.search(qv, topKRecall);
            for (int i = 0; i < hits.size(); i++) {
                VectorHit h = hits.get(i);
                vectorRank.put(h.id(), i + 1);
                metaById.put(h.id(), toChunk(h.id(), h.content(), h.metadataJson(), 0));
            }
        } catch (Exception e) {
            log.warn("向量检索失败，降级为关键词检索", e);
        }
        Map<String, Integer> keywordRank = new LinkedHashMap<>();
        if (hybridEnabled) {
            try {
                List<DocChunk> kwHits = chunkMapper.fullTextSearch(query, topKRecall);
                for (int i = 0; i < kwHits.size(); i++) {
                    DocChunk c = kwHits.get(i);
                    keywordRank.put(c.getVectorId(), i + 1);
                    // 关键词命中不带向量元数据，缺时才回查 MySQL 文档属性补齐
                    metaById.computeIfAbsent(c.getVectorId(), vid -> buildMeta(c));
                }
            } catch (Exception e) {
                // MySQL ngram 全文检索失败时同样只记 warn，保留向量路结果
                log.warn("全文检索失败，降级为向量检索", e);
            }
        }
        Map<String, Double> rrf = new HashMap<>();
        for (String id : metaById.keySet()) {
            // RRF 累加两路名次的倒数得分：只命中一路的候选也能进入结果
            rrf.put(id, rrfScore(vectorRank.get(id), keywordRank.get(id)));
        }
        List<RetrievedChunk> result = new ArrayList<>();
        for (Map.Entry<String, Double> e : rrf.entrySet()) {
            RetrievedChunk c = metaById.get(e.getKey());
            result.add(new RetrievedChunk(c.vectorId(), c.content(), c.documentId(), c.fileName(),
                    c.category(), c.clauseRef(), c.sourceUrl(), e.getValue()));
        }
        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        return result;
    }

    /** RRF 融合得分：rank 越小（越靠前）得分越高。 */
    public static double rrfScore(Integer vectorRank, Integer keywordRank) {
        double score = 0;
        if (vectorRank != null) score += 1.0 / (60 + vectorRank);
        if (keywordRank != null) score += 1.0 / (60 + keywordRank);
        return score;
    }

    private RetrievedChunk toChunk(String vectorId, String content, String metadataJson, double score) {
        long docId = 0; String fileName = ""; String category = ""; String clauseRef = ""; String sourceUrl = "";
        try {
            JsonNode m = objectMapper.readTree(metadataJson);
            docId = m.path("documentId").asLong();
            fileName = m.path("fileName").asText("");
            category = m.path("category").asText("");
            clauseRef = m.path("clauseRef").asText("");
            sourceUrl = m.path("sourceUrl").asText("");
        } catch (Exception e) {
            log.warn("解析向量元数据失败: vectorId={}", vectorId, e);
        }
        return new RetrievedChunk(vectorId, content, docId, fileName, category, clauseRef, sourceUrl, score);
    }

    private RetrievedChunk buildMeta(DocChunk c) {
        String fileName = "", category = "", sourceUrl = "";
        KbDocument d = documentMapper.selectById(c.getDocumentId());
        if (d != null) {
            fileName = d.getFileName() == null ? "" : d.getFileName();
            category = d.getCategoryId() == null ? "" : String.valueOf(d.getCategoryId());
            sourceUrl = d.getSourceUrl() == null ? "" : d.getSourceUrl();
        }
        return new RetrievedChunk(c.getVectorId(), c.getContent() == null ? "" : c.getContent(),
                c.getDocumentId(), fileName, category, c.getClauseRef() == null ? "" : c.getClauseRef(), sourceUrl, 0);
    }
}
