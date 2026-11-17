package com.campuspedia.mapper;

/**
 * 系统配置模块数据访问层文件，对应 t_sys_config（RAG 参数与提示词键值对）。
 * 所属 mapper 层：按 configKey 等值读取，无自定义 SQL。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/** 继承 MyBatis-Plus BaseMapper 提供 t_sys_config 单表 CRUD，按 configKey 等值读取，无自定义 SQL。 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {}
