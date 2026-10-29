package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.KbDocument;
import com.campuspedia.service.KnowledgeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
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

    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }
}
