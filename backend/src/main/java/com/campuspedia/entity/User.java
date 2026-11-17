package com.campuspedia.entity;

/**
 * 用户表：学生与管理员账号，登录与权限主体。
 * 对应数据表 t_user，所属 entity 层，由 UserMapper（继承 MyBatis-Plus BaseMapper）映射。
 * studentNo 为登录账号且建有唯一索引 uk_student_no；本表被多张表以 user_id 逻辑关联，全库无物理外键，一致性由应用层保证。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_user 实体。username=姓名，studentNo=学号。 */
@Data
@TableName("t_user")
public class User {
    /** 用户ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 学号，登录账号，库内 uk_student_no 唯一；与 username（姓名）含义不同。 */
    private String studentNo;
    /** 姓名；登录认证请用 studentNo。 */
    private String username;
    /** 密码，BCrypt 哈希存储，禁止明文。 */
    private String password;
    /** 专业，可空。 */
    private String major;
    /** 班级，对应列 class_name，可空。 */
    private String className;
    /** 角色：user 普通用户 / admin 管理员，库默认 'user'。 */
    private String role;
    /** 账号状态：1 启用 / 0 禁用，库默认 1。 */
    private Integer status;
    /** 头像地址，库默认空串。 */
    private String avatar;
    /** 注册时间，库默认 CURRENT_TIMESTAMP。 */
    private LocalDateTime createTime;
}
