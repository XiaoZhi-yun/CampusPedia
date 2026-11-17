package com.campuspedia.service;
/**
 * 知识库文档管理，隶属业务服务层（service 包），编排上传、解析、切片、向量化与级联删除。
 *
 * <p>上游：知识库 Controller；下游：{@code DocumentLoader}/{@code Chunker}/{@code RecursiveSplitter}（解析切分）、
 * {@code EmbeddingService}（编码）、{@code PgVectorStore}（向量库）、{@code DocChunkMapper}/{@code KbDocumentMapper}（MySQL）、
 * 以及本地文件存储。是写入侧唯一入口，读取检索由 {@code RagChain} 另行驱动。</p>
 */

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

/**
 * 知识库文档的写入侧门面：保证文件、文档记录、切片与向量四处数据尽量一致。
 *
 * <p>上传与向量化会跨本地文件系统、MySQL、pgvector 三类存储，无法用单一事务覆盖，
 * 因此写入失败通过 {@code vectorStatus} 标记、删除失败通过 warn 日志留痕，容忍部分残留。</p>
 */
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

    /** 原始文件存储根目录，默认 uploads；取绝对路径后再做路径穿越校验。 */
    @Value("${campus.storage.path:uploads}")
    private String storagePath;

    /**
     * 组装写入侧全部依赖。
     *
     * @param documentMapper 文档表 Mapper
     * @param chunkMapper    切片表 Mapper
     * @param loader         文档正文抽取
     * @param splitter       通用递归切片器
     * @param chunker        法规条款切片器
     * @param embedding      向量化实现
     * @param vectorStore    pgvector 访问
     * @param configService  切片参数等运行时配置
     */
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

    /**
     * 保存上传文件、登记文档并立即触发向量化。
     *
     * <p>副作用：写本地文件、插入文档记录，并同步调用 {@code vectorize} 写切片与向量；
     * 返回的文档已刷新，含最终 {@code vectorStatus} 与 {@code chunkCount}。</p>
     *
     * @param fileName   原始文件名，先经净化再落盘
     * @param bytes      文件字节
     * @param categoryId 分类 id，为空按 0 处理
     * @param sourceUrl  原文链接，为空按空串处理
     * @return 刷新后的文档记录
     * @throws BizException 文件名非法、保存失败或向量化失败时抛出
     */
    public KbDocument upload(String fileName, byte[] bytes, Long categoryId, String sourceUrl) {
        // 入口即净化，保证后续 filePath、fileName、扩展名都基于安全名派生
        String safeName = sanitizeFileName(fileName);
        String filePath = saveFile(safeName, bytes);
        KbDocument doc = new KbDocument();
        doc.setFileName(safeName);
        doc.setFileType(ext(safeName));
        doc.setFileSize(bytes.length);
        doc.setFilePath(filePath);
        doc.setSourceUrl(sourceUrl == null ? "" : sourceUrl);
        doc.setCategoryId(categoryId == null ? 0L : categoryId);
        // pending → done/fail 状态机：先登记再向量化，异常时回落为 fail
        doc.setVectorStatus("pending");
        documentMapper.insert(doc);
        vectorize(doc.getId());
        return documentMapper.selectById(doc.getId());
    }

    /**
     * 抽取文档正文、切片、编码后写入 pgvector 与切片表，并回写状态。
     *
     * <p>法规类文档（文件名含办法/规定/条例等）改用 {@code Chunker} 以保留条款引用，
     * 其余走递归切片器；每个切片以同一 {@code vectorId} 双写向量库与 MySQL，
     * 供后续按 {@code documentId} 级联删除。失败时先把 {@code vectorStatus} 置为 fail 再抛出。</p>
     *
     * @param documentId 文档 id
     * @throws BizException 文档不存在或解析、写入失败
     */
    public void vectorize(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) throw new BizException("文档不存在");
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(doc.getFilePath()));
            String text = loader.load(doc.getFileName(), bytes);
            // 切片大小/重叠取自 t_sys_config，缺省 500/80 保证无配置时也能建库
            int chunkSize = configService.getInt("chunk_size", 500);
            int overlap = configService.getInt("chunk_overlap", 80);
            List<String> texts;
            List<String> refs;
            // 法规类文档按条款切分以保留 clauseRef，其余按字符窗口切分
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
                // 同一 vectorId 双写向量库与切片表，删除时靠 metadata.documentId 反查
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

    /**
     * 列出全部文档记录。
     *
     * @return 文档列表（全量，不分页），供后台管理页展示
     */
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
            // 向量与文件属事务外资源，删除失败只记 warn、不回滚 MySQL
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
        // 超长时截尾保留扩展名，避免触发文件系统文件名长度限制
        if (safe.length() > 120) safe = safe.substring(safe.length() - 120);
        return safe;
    }

    /**
     * 把文件写入 {@code storagePath/original}，返回绝对路径供回查。
     *
     * <p>文件名前缀随机 UUID 避免重名覆盖；resolve 后 normalize 并校验仍在目标目录内，
     * 作为净化之后的第二道路径穿越防线。</p>
     *
     * @param fileName 已净化的文件名
     * @param bytes    文件字节
     * @return 落盘文件的绝对路径
     * @throws BizException 路径越界或写盘失败
     */
    private String saveFile(String fileName, byte[] bytes) {
        try {
            Path dir = Paths.get(storagePath, "original").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String saved = UUID.randomUUID() + "_" + fileName;
            Path p = dir.resolve(saved).normalize();
            // resolve + normalize 后仍须落在 dir 内，兜住净化遗漏的 ../ 逃逸
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
