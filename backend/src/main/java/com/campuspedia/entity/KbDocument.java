package com.campuspedia.entity;

/**
 * 语料文档表：上传的制度文件及向量化状态。
 * 对应数据表 t_kb_document，所属 entity 层，由 KbDocumentMapper 映射。
 * category_id 逻辑关联 t_kb_category.id，全库无物理外键；vector_status 由向量化任务回写，
 * file_type 供 StatsController 统计文档类型分布。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_kb_document 实体。 */
@Data
@TableName("t_kb_document")
public class KbDocument {
    /** 文档ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属分类ID，逻辑关联 t_kb_category.id；库默认 0 表示未分类，无物理外键。 */
    private Long categoryId;
    /** 原始文件名，上传时已净化。 */
    private String fileName;
    /** 文件扩展名；KbDocumentMapper.countByType 按此分组统计类型分布。 */
    private String fileType;
    /** 文件大小，单位字节，库默认 0。 */
    private Integer fileSize;
    /** 服务器落盘路径，库默认空串。 */
    private String filePath;
    /** 官网原文链接，仅接受 http/https，库默认空串。 */
    private String sourceUrl;
    /** 向量化状态：pending 待处理 / done 已入库 / fail 失败，库默认 pending，由向量化任务回写。 */
    private String vectorStatus;
    /** 切片数量，库默认 0，应与 t_doc_chunk 实际记录数一致。 */
    private Integer chunkCount;
    /** 上传时间，库默认 CURRENT_TIMESTAMP。 */
    private LocalDateTime createTime;
    /** 最后更新时间，库 ON UPDATE CURRENT_TIMESTAMP 自动维护。 */
    private LocalDateTime updateTime;
}