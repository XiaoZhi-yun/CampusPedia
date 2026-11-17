package com.campuspedia.mapper;

/**
 * 语料分类模块数据访问层文件，对应 t_kb_category（含「其他」兜底分类）。
 * 所属 mapper 层：分类仅少量行，列表按 sort_num 升序，无需自定义 SQL。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.KbCategory;
import org.apache.ibatis.annotations.Mapper;

/** 继承 MyBatis-Plus BaseMapper 提供 t_kb_category 单表 CRUD，无自定义 SQL。 */
@Mapper
public interface KbCategoryMapper extends BaseMapper<KbCategory> {}
