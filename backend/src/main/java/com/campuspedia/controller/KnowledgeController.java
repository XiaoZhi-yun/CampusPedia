package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.DocChunk;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.mapper.DocChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campuspedia.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    private static final int MAX_BATCH = 50;

    private final KnowledgeService knowledgeService;
    private final DocChunkMapper chunkMapper;

    public KnowledgeController(KnowledgeService knowledgeService, DocChunkMapper chunkMapper) {
        this.knowledgeService = knowledgeService;
        this.chunkMapper = chunkMapper;
    }

    @PostMapping("/documents")
    public Result<KbDocument> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "categoryId", defaultValue = "0") Long categoryId,
                                     @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        String name = file.getOriginalFilename();
        if (name == null || !name.matches("(?i).*\\.(md|txt|pdf|doc|docx)$")) {
            return Result.error("仅支持 md / txt / pdf / doc / docx 文件");
        }
        if (file.isEmpty()) {
            return Result.error("文件内容为空");
        }
        if (sourceUrl != null && !sourceUrl.isBlank() && !sourceUrl.matches("(?i)^https?://.*")) {
            return Result.error("来源链接仅支持 http / https");
        }
        try {
            return Result.ok(knowledgeService.upload(name, file.getBytes(), categoryId, sourceUrl));
        } catch (Exception e) {
            log.error("语料上传失败: {}", name, e);
            return Result.error("上传失败，请检查文件后重试");
        }
    }

    @PostMapping("/documents/{id}/vectorize")
    public Result<Void> vectorize(@PathVariable Long id) {
        knowledgeService.vectorize(id);
        return Result.ok(null);
    }

    @GetMapping("/documents")
    public Result<List<KbDocument>> list() {
        return Result.ok(knowledgeService.list());
    }

    @GetMapping("/documents/{id}/chunks")
    public Result<List<DocChunk>> chunks(@PathVariable Long id) {
        return Result.ok(chunkMapper.selectList(new QueryWrapper<DocChunk>().eq("document_id", id).orderByAsc("chunk_index")));
    }

    @PostMapping("/documents/batch-vectorize")
    public Result<Void> batchVectorize(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Result.error("请选择要向量化的文档");
        }
        if (list.size() > MAX_BATCH) {
            return Result.error("单次最多处理 " + MAX_BATCH + " 篇文档");
        }
        List<Long> ids = list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        for (Long id : ids) knowledgeService.vectorize(id);
        return Result.ok(null);
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }
}
