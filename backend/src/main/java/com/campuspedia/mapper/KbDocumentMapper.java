package com.campuspedia.mapper;

/**
 * 语料文档模块数据访问层文件，对应 t_kb_document（上传文档及向量化状态）。
 * 所属 mapper 层：除 BaseMapper CRUD 外，另有按 file_type 分组的类型分布统计。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 继承 MyBatis-Plus BaseMapper 提供 t_kb_document 单表 CRUD，另按 file_type 分组统计类型分布。 */
@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 统计各文件类型的文档数量，供 StatsController 的文档类型分布图使用。
     * 直接对 t_kb_document 按 file_type 分组 COUNT(*)，口径为全部已上传文档（不过滤 vector_status），依赖 idx_file_type；
     * 无入参，返回每行 {type, count} 的 Map 列表。
     */
    @Select("SELECT file_type AS type, COUNT(*) AS count FROM t_kb_document GROUP BY file_type")
    List<Map<String, Object>> countByType();

}
