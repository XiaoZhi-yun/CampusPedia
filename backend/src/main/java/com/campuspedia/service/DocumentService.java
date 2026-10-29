package com.campuspedia.service;

import com.campuspedia.domain.Chunk;
import com.campuspedia.domain.Document;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档导入服务：编排"文本提取 → 切片 → 向量化 → 入库"全流程。
 *
 * <p>阶段 1 仅支持文本类格式（.md/.txt 等）；PDF/Word 提取在后续阶段接入。
 * 文档元信息保存在内存 Map 中，切片写入 {@link VectorStore}。</p>
 */
@Service
public class DocumentService {

    private final Chunker chunker;
    private final EmbeddingService embedding;
    private final VectorStore vectorStore;

    /** 文档元信息索引（id → 文档），保证列表顺序与插入顺序一致。 */
    private final Map<String, Document> documents = new LinkedHashMap<>();

    public DocumentService(Chunker chunker, EmbeddingService embedding, VectorStore vectorStore) {
        this.chunker = chunker;
        this.embedding = embedding;
        this.vectorStore = vectorStore;
    }

    /**
     * 导入一篇文档：提取文本 → 切片 → 逐片向量化 → 写入向量库。
     *
     * @param fileName 文件名
     * @param category 类目
     * @param bytes    文件原始字节
     * @return 导入后的文档元信息
     */
    public Document ingest(String fileName, String category, String sourceUrl, byte[] bytes) {
        String text = extractText(fileName, bytes);
        String id = UUID.randomUUID().toString();

        // 切片 → 向量化 → 入库
        List<ChunkText> chunkTexts = chunker.chunk(text);
        List<Chunk> chunks = new ArrayList<>();
        for (ChunkText ct : chunkTexts) {
            Chunk chunk = new Chunk(
                    UUID.randomUUID().toString(),
                    id,
                    fileName,
                    category,
                    ct.clauseRef(),
                    ct.text(),
                    embedding.embed(ct.text()),
                    sourceUrl
            );
            chunks.add(chunk);
            vectorStore.add(chunk);
        }

        Document doc = new Document(id, fileName, category, sourceUrl, text.length(), chunks.size(), Instant.now());
        documents.put(id, doc);
        return doc;
    }

    /** 返回全部已导入文档（按导入顺序）。 */
    public List<Document> list() {
        return new ArrayList<>(documents.values());
    }

    /**
     * 删除文档：移除元信息并清理其全部切片。
     *
     * @param id 文档 id
     */
    public void delete(String id) {
        documents.remove(id);
        vectorStore.removeByDocument(id);
    }

    /** 返回当前切片总数（用于验收与监控）。 */
    public int chunkCount() {
        return vectorStore.count();
    }

    /** 返回当前向量化服务维度（诊断用：hash=128 / dashscope=1024）。 */
    public int embeddingDimension() {
        return embedding.dimension();
    }

    /**
     * 按扩展名提取纯文本。
     *
     * @throws IllegalArgumentException 不支持的格式（如 PDF/Word，后续阶段支持）
     */
    private String extractText(String fileName, byte[] bytes) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".markdown")
                || lower.endsWith(".json") || lower.endsWith(".csv")) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("暂不支持的文件格式：" + fileName + "（当前支持 .md/.txt，PDF/Word 后续阶段支持）");
    }
}
