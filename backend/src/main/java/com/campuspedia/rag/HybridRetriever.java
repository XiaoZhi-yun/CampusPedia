package com.campuspedia.rag;

import com.campuspedia.entity.DocChunk;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.mapper.DocChunkMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import com.campuspedia.service.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridRetriever {
    private final PgVectorStore vectorStore;
    private final EmbeddingService embedding;
    private final DocChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HybridRetriever(PgVectorStore vectorStore, EmbeddingService embedding,
                           DocChunkMapper chunkMapper, KbDocumentMapper documentMapper) {
        this.vectorStore = vectorStore;
        this.embedding = embedding;
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    public List<RetrievedChunk> retrieve(String query, int topKRecall, boolean hybridEnabled) {
        Map<String, Integer> vectorRank = new LinkedHashMap<>();
        Map<String, RetrievedChunk> metaById = new LinkedHashMap<>();
        try {
            float[] qv = embedding.embed(query);
            List<VectorHit> hits = vectorStore.search(qv, topKRecall);
            for (int i = 0; i < hits.size(); i++) {
                VectorHit h = hits.get(i);
                vectorRank.put(h.id(), i + 1);
                metaById.put(h.id(), toChunk(h.id(), h.content(), h.metadataJson(), 0));
            }
        } catch (Exception ignore) {}
        Map<String, Integer> keywordRank = new LinkedHashMap<>();
        if (hybridEnabled) {
            try {
                List<DocChunk> kwHits = chunkMapper.fullTextSearch(query, topKRecall);
                for (int i = 0; i < kwHits.size(); i++) {
                    DocChunk c = kwHits.get(i);
                    keywordRank.put(c.getVectorId(), i + 1);
                    metaById.computeIfAbsent(c.getVectorId(), vid -> buildMeta(c));
                }
            } catch (Exception ignore) {}
        }
        Map<String, Double> rrf = new HashMap<>();
        for (String id : metaById.keySet()) {
            double score = 0;
            Integer vr = vectorRank.get(id);
            Integer kr = keywordRank.get(id);
            if (vr != null) score += 1.0 / (60 + vr);
            if (kr != null) score += 1.0 / (60 + kr);
            rrf.put(id, score);
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

    private RetrievedChunk toChunk(String vectorId, String content, String metadataJson, double score) {
        long docId = 0; String fileName = ""; String category = ""; String clauseRef = ""; String sourceUrl = "";
        try {
            JsonNode m = objectMapper.readTree(metadataJson);
            docId = m.path("documentId").asLong();
            fileName = m.path("fileName").asText("");
            category = m.path("category").asText("");
            clauseRef = m.path("clauseRef").asText("");
            sourceUrl = m.path("sourceUrl").asText("");
        } catch (Exception ignore) {}
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
