package com.campuspedia.mapper;

/**
 * 用户模块数据访问层文件，对应 t_user（用户账号与权限主体）。
 * 所属 mapper 层：单表 CRUD 由 MyBatis-Plus BaseMapper 提供；表间关联不建物理外键，由应用层约束。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 继承 MyBatis-Plus BaseMapper 提供 t_user 单表 CRUD；登录按 studentNo 等值查询（uk_student_no），无自定义 SQL。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {}
