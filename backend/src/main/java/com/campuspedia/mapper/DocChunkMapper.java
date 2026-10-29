package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.DocChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {
    @Select("SELECT * FROM t_doc_chunk WHERE MATCH(content) AGAINST(#{kw} IN NATURAL LANGUAGE MODE) LIMIT #{limit}")
    List<DocChunk> fullTextSearch(@Param("kw") String kw, @Param("limit") int limit);
}
