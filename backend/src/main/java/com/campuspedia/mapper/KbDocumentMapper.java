package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    @Select("SELECT file_type AS type, COUNT(*) AS count FROM t_kb_document GROUP BY file_type")
    List<Map<String, Object>> countByType();

    @Select("SELECT COALESCE(MAX(id), 0) FROM t_kb_document")
    Long selectMaxId();

    @Update("ALTER TABLE t_kb_document AUTO_INCREMENT = #{n}")
    void resetAutoIncrement(@Param("n") long n);
}
