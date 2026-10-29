package com.campuspedia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_sys_log 实体。 */
@Data
@TableName("t_sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String role;
    private String action;
    private String detail;
    private String ip;
    private LocalDateTime createTime;
}