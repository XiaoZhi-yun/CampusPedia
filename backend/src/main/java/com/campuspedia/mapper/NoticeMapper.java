package com.campuspedia.mapper;

/**
 * 公告模块数据访问层文件，对应 t_notice（置顶优先展示的系统公告）。
 * 所属 mapper 层：公告排序由 Service 层处理，无自定义 SQL。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
/** 继承 MyBatis-Plus BaseMapper 提供 t_notice 单表 CRUD，无自定义 SQL。 */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}
