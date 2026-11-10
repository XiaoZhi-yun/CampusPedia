package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    @Select("SELECT file_type AS type, COUNT(*) AS count FROM t_kb_document GROUP BY file_type")
    List<Map<String, Object>> countByType();
}
