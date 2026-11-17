package com.campuspedia.mapper;

/**
 * 问答日志模块数据访问层文件，对应 t_sys_log（每次提问的审计留痕）。
 * 所属 mapper 层：当前只承载写入，统计不依赖本表，故无自定义查询 SQL。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;

/** 继承 MyBatis-Plus BaseMapper 提供 t_sys_log 单表 CRUD（当前仅写入），无自定义查询 SQL。 */
@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {}
