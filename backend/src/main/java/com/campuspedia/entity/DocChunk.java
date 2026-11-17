package com.campuspedia.entity;

/**
 * 文档切片表：语料切分后的检索单元，与 pgvector 向量一一对应。
 * 对应数据表 t_doc_chunk，所属 entity 层，由 DocChunkMapper 映射。
 * document_id 逻辑关联 t_kb_document.id，全库无物理外键；content 建有 MySQL ngram 全文索引，
 * 供 MATCH(content) AGAINST 检索，vectorId 指向向量库中的向量。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_doc_chunk 实体。 */
@Data
@TableName("t_doc_chunk")
public class DocChunk {
    /** 切片ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属文档ID，逻辑关联 t_kb_document.id，无物理外键；删文档时由应用层据此级联删切片。 */
    private Long documentId;
    /** 切片序号，从 0 开始；与 documentId 组成联合索引，支撑按顺序预览。 */
    private Integer chunkIndex;
    /** 切片正文，建有 MySQL ngram 全文索引，供 MATCH(content) AGAINST 检索。 */
    private String content;
    /** 切片字符数，库默认 0。 */
    private Integer charCount;
    /** 条款引用，法规类文档适用，库默认空串。 */
    private String clauseRef;
    /** pgvector 向量标识，与向量库向量一一对应（业务应唯一，未建唯一约束）。 */
    private String vectorId;
}