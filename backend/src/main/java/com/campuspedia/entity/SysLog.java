package com.campuspedia.entity;

/**
 * 问答日志表：记录每次提问的操作审计（当前只写入、无读取接口）。
 * 对应数据表 t_sys_log，所属 entity 层，由 SysLogMapper 映射。
 * ChatController.stream() 每次提问写一条（action 固定 chat，username 为 userId 或「游客」），
 * 写入失败仅告警不阻断问答；问答量与热门问题统计口径另取 t_chat_message，勿与本表混用。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_sys_log 实体。 */
@Data
@TableName("t_sys_log")
public class SysLog {
    /** 日志ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 操作者标识，当前存 userId 或「游客」，可空；非 t_user 外键，仅作审计留痕。 */
    private String username;
    /** 操作者角色，可空。 */
    private String role;
    /** 操作类型，当前固定为 chat（每次提问），预留扩展。 */
    private String action;
    /** 操作详情，当前为提问原文前 200 字。 */
    private String detail;
    /** 来源 IP，可空。 */
    private String ip;
    /** 记录时间，库默认 CURRENT_TIMESTAMP；供审计范围查询与定期归档（idx_log_create_time）。 */
    private LocalDateTime createTime;
}