package com.campuspedia.controller;

import com.campuspedia.domain.Document;
import com.campuspedia.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文档管理 REST 接口。
 *
 * <ul>
 *   <li>POST /api/documents —— 上传文档（multipart：file + category）</li>
 *   <li>GET  /api/documents —— 文档列表</li>
 *   <li>DELETE /api/documents/{id} —— 删除文档</li>
 *   <li>GET  /api/documents/chunks/count —— 切片总数</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传并导入文档。
     *
     * @param file     上传文件
     * @param category 类目（默认"未分类"）
     * @return 文档元信息；格式不支持时返回 400
     */
    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "未分类") String category) {
        try {
            Document doc = documentService.ingest(file.getOriginalFilename(), category, file.getBytes());
            return ResponseEntity.ok(doc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "读取文件失败：" + e.getMessage()));
        }
    }

    /** 文档列表。 */
    @GetMapping
    public List<Document> list() {
        return documentService.list();
    }

    /** 删除文档。 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }

    /** 切片总数。 */
    @GetMapping("/chunks/count")
    public Map<String, Object> chunkCount() {
        return Map.of("totalChunks", documentService.chunkCount());
    }

    /** 向量化诊断：维度可区分 hash(128) / dashscope(1024)。 */
    @GetMapping("/embedding-info")
    public Map<String, Object> embeddingInfo() {
        return Map.of("embeddingDimension", documentService.embeddingDimension());
    }
}
