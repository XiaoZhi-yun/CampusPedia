package com.campuspedia.controller;
/**
 * 控制层：知识库语料的上传、向量化、列表与删除，挂载于 /api/kb。
 * <p>负责入口参数白名单校验，解析、切片与向量化委托 KnowledgeService。</p>
 */

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

/**
 * 知识库语料接口：上传、向量化、列举、查看切片与删除。
 * <p>上传入口负责扩展名/协议/批量规模校验，具体处理委托 KnowledgeService。</p>
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    /** 单次批量向量化的文档数上限，约束一次请求可触发的下游任务规模。 */
    private static final int MAX_BATCH = 50;

    private final KnowledgeService knowledgeService;
    private final DocChunkMapper chunkMapper;

    /**
     * 注入知识库服务与切片 Mapper。
     *
     * @param knowledgeService 处理上传、解析、切片与向量化
     * @param chunkMapper      按文档查询切片
     */
    public KnowledgeController(KnowledgeService knowledgeService, DocChunkMapper chunkMapper) {
        this.knowledgeService = knowledgeService;
        this.chunkMapper = chunkMapper;
    }

    /**
     * 上传语料：先做扩展名/空文件/来源协议白名单校验，再交给服务层解析入库。
     * <p>校验与处理失败均返回固定错误文案，不回显底层异常细节。</p>
     *
     * @param file       上传的语料文件
     * @param categoryId 所属分类，缺省为 0
     * @param sourceUrl  可选的来源链接，仅允许 http/https
     * @return 新建的文档记录；参数不合规或处理失败时返回错误响应
     */
    @PostMapping("/documents")
    public Result<KbDocument> upload(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "categoryId", defaultValue = "0") Long categoryId,
                                     @RequestParam(value = "sourceUrl", required = false) String sourceUrl) {
        String name = file.getOriginalFilename();
        /** 扩展名白名单：只放行可解析格式，避免把可执行文件/未知类型喂给解析与向量化流程。 */
        if (name == null || !name.matches("(?i).*\\.(md|txt|pdf|doc|docx)$")) {
            return Result.error("仅支持 md / txt / pdf / doc / docx 文件");
        }
        /** 空文件直接拒绝，避免产生无内容文档与无效向量。 */
        if (file.isEmpty()) {
            return Result.error("文件内容为空");
        }
        /** 仅允许 http/https，阻断 file:// 等协议引发的 SSRF 或本地文件读取。 */
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

    /**
     * 对指定文档执行向量化，副作用是生成并写入切片向量。
     *
     * @param id 文档主键
     * @return 无 data 的成功响应
     */
    @PostMapping("/documents/{id}/vectorize")
    public Result<Void> vectorize(@PathVariable Long id) {
        knowledgeService.vectorize(id);
        return Result.ok(null);
    }

    /**
     * 列出全部知识库文档。
     *
     * @return 文档列表
     */
    @GetMapping("/documents")
    public Result<List<KbDocument>> list() {
        return Result.ok(knowledgeService.list());
    }

    /**
     * 按 chunk_index 升序列出指定文档的切片，供管理端核对分片结果。
     *
     * @param id 文档主键
     * @return 该文档的有序切片列表
     */
    @GetMapping("/documents/{id}/chunks")
    public Result<List<DocChunk>> chunks(@PathVariable Long id) {
        return Result.ok(chunkMapper.selectList(new QueryWrapper<DocChunk>().eq("document_id", id).orderByAsc("chunk_index")));
    }

    /**
     * 批量向量化：校验 ids 非空且不超过上限后逐个处理。
     * <p>副作用是连续触发多次向量化；超限直接返回错误，避免长任务拖垮服务。</p>
     *
     * @param body 含 ids 列表的请求体
     * @return 无 data 的成功响应；ids 为空或超限时返回错误响应
     */
    @PostMapping("/documents/batch-vectorize")
    public Result<Void> batchVectorize(@RequestBody Map<String, Object> body) {
        Object raw = body.get("ids");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Result.error("请选择要向量化的文档");
        }
        /** ids 由前端传入不可信，超限直接拒绝而非截断，避免静默丢弃部分文档。 */
        if (list.size() > MAX_BATCH) {
            return Result.error("单次最多处理 " + MAX_BATCH + " 篇文档");
        }
        List<Long> ids = list.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        for (Long id : ids) knowledgeService.vectorize(id);
        return Result.ok(null);
    }

    /**
     * 删除指定文档及其关联数据，副作用由服务层完成。
     *
     * @param id 文档主键
     * @return 无 data 的成功响应
     */
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok(null);
    }
}
