package com.campuspedia.service;

import com.campuspedia.common.BizException;
import com.campuspedia.entity.DocChunk;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.mapper.DocChunkMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import com.campuspedia.rag.DocumentLoader;
import com.campuspedia.rag.PgVectorStore;
import com.campuspedia.rag.RecursiveSplitter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final KbDocumentMapper documentMapper;
    private final DocChunkMapper chunkMapper;
    private final DocumentLoader loader;
    private final RecursiveSplitter splitter;
    private final Chunker chunker;
    private final EmbeddingService embedding;
    private final PgVectorStore vectorStore;
    private final ConfigService configService;

    @Value("${campus.storage.path:uploads}")
    private String storagePath;

    public KnowledgeService(KbDocumentMapper documentMapper, DocChunkMapper chunkMapper,
                            DocumentLoader loader, RecursiveSplitter splitter, Chunker chunker,
                            EmbeddingService embedding, PgVectorStore vectorStore, ConfigService configService) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.loader = loader;
        this.splitter = splitter;
        this.chunker = chunker;
        this.embedding = embedding;
        this.vectorStore = vectorStore;
        this.configService = configService;
    }

    public KbDocument upload(String fileName, byte[] bytes, Long categoryId, String sourceUrl) {
        String safeName = sanitizeFileName(fileName);
        String filePath = saveFile(safeName, bytes);
        KbDocument doc = new KbDocument();
        doc.setFileName(safeName);
        doc.setFileType(ext(safeName));
        doc.setFileSize(bytes.length);
        doc.setFilePath(filePath);
        doc.setSourceUrl(sourceUrl == null ? "" : sourceUrl);
        doc.setCategoryId(categoryId == null ? 0L : categoryId);
        doc.setVectorStatus("pending");
        documentMapper.insert(doc);
        vectorize(doc.getId());
        return documentMapper.selectById(doc.getId());
    }

    public void vectorize(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BizException("文档不存在");
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(doc.getFilePath()));
            String text = loader.load(doc.getFileName(), bytes);
            int chunkSize = configService.getInt("chunk_size", 500);
            int overlap = configService.getInt("chunk_overlap", 80);
            List<String> texts;
            List<String> refs;
            if (doc.getFileName().matches(".*(办法|规定|条例|细则|规则|守则).*")) {
                List<ChunkText> cts = chunker.chunk(text);
                texts = cts.stream().map(ChunkText::text).toList();
                refs = cts.stream().map(ChunkText::clauseRef).toList();
            } else {
                texts = splitter.split(text, chunkSize, overlap);
                refs = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) refs.add("");
            }
            for (int i = 0; i < texts.size(); i++) {
                String chunkText = texts.get(i);
                String vectorId = UUID.randomUUID().toString();
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("documentId", doc.getId());
                meta.put("fileName", doc.getFileName());
                meta.put("category", String.valueOf(doc.getCategoryId()));
                meta.put("clauseRef", refs.get(i));
                meta.put("sourceUrl", doc.getSourceUrl());
                meta.put("chunkIndex", i);
                vectorStore.add(vectorId, chunkText, meta, embedding.embed(chunkText));
                DocChunk c = new DocChunk();
                c.setDocumentId(doc.getId());
                c.setChunkIndex(i);
                c.setContent(chunkText);
                c.setCharCount(chunkText.length());
                c.setClauseRef(refs.get(i));
                c.setVectorId(vectorId);
                chunkMapper.insert(c);
            }
            doc.setChunkCount(texts.size());
            doc.setVectorStatus("done");
            documentMapper.updateById(doc);
        } catch (BizException e) {
            doc.setVectorStatus("fail");
            documentMapper.updateById(doc);
            throw e;
        } catch (Exception e) {
            doc.setVectorStatus("fail");
            documentMapper.updateById(doc);
            log.error("语料向量化失败: documentId={}", documentId, e);
            throw new BizException("向量化失败：" + e.getMessage());
        }
    }

    public List<KbDocument> list() { return documentMapper.selectList(null); }

    /** 级联删除；MySQL 侧由事务保证原子，向量库/文件失败保留容错但必须留日志。 */
    @Transactional
    public void delete(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        try {
            chunkMapper.delete(new QueryWrapper<DocChunk>().eq("document_id", documentId));
        } catch (Exception e) {
            log.warn("删除切片失败，可能残留数据: documentId={}", documentId, e);
        }
        try {
            vectorStore.removeByDocument(String.valueOf(documentId));
        } catch (Exception e) {
            log.warn("删除向量失败，可能残留数据: documentId={}", documentId, e);
        }
        documentMapper.deleteById(documentId);
        if (doc != null && doc.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(doc.getFilePath()));
            } catch (Exception e) {
                log.warn("删除原始文件失败: {}", doc.getFilePath(), e);
            }
        }
    }

    /** 去掉目录与非法字符，防止上传文件名路径穿越覆盖项目文件。 */
    private String sanitizeFileName(String fileName) {
        String raw = fileName == null ? "" : fileName.replace('\\', '/');
        String base;
        try {
            base = Paths.get(raw).getFileName().toString();
        } catch (Exception e) {
            base = "upload.bin";
        }
        String safe = base.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) safe = "upload.bin";
        if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
        return safe;
    }

    private String saveFile(String fileName, byte[] bytes) {
        try {
            Path dir = Paths.get(storagePath, "original").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String saved = UUID.randomUUID() + "_" + fileName;
            Path p = dir.resolve(saved).normalize();
            if (!p.startsWith(dir)) throw new BizException("非法文件名");
            Files.write(p, bytes);
            return p.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }
    }

    private String ext(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i >= 0 ? fileName.substring(i + 1) : "";
    }
}
