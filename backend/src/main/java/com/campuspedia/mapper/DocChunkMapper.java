package com.campuspedia.mapper;

/**
 * 文档切片模块数据访问层文件，对应 t_doc_chunk（检索单元，与 pgvector 向量对应）。
 * 所属 mapper 层：除 BaseMapper CRUD 外，另有基于 ngram 全文索引的 MATCH ... AGAINST 检索。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.DocChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 继承 MyBatis-Plus BaseMapper 提供 t_doc_chunk 单表 CRUD，另提供基于 ngram 全文索引的检索。 */
@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {
    /**
     * 基于 MySQL ngram 全文索引检索切片正文，供 RAG 召回阶段使用。
     * 依赖 content 上的 FULLTEXT 索引与 MATCH(content) AGAINST(... IN NATURAL LANGUAGE MODE) 自然语言模式；
     * 参数：#{kw} 绑定 @Param("kw") 关键词，#{limit} 绑定 @Param("limit") 返回条数上限；
     * 返回匹配的 DocChunk 列表，无命中时为空列表。
     */
    @Select("SELECT * FROM t_doc_chunk WHERE MATCH(content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) LIMIT #{limit}")
    List<DocChunk> fullTextSearch(@Param("kw") String kw, @Param("limit") int limit);
}
