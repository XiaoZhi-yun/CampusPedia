package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.DocChunk;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.mapper.DocChunkMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campuspedia.service.KnowledgeService;
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
        try {
            return Result.ok(knowledgeService.upload(file.getOriginalFilename(), file.getBytes(), categoryId, sourceUrl));
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
        List<Long> ids = ((List<?>) body.get("ids")).stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        for (Long id : ids) knowledgeService.vectorize(id);
        return Result.ok(null);
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }
}
