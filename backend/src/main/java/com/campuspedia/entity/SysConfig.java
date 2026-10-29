package com.campuspedia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_sys_config 实体。 */
@Data
@TableName("t_sys_config")
public class SysConfig {
    @TableId
    private String configKey;
    private String configValue;
    private String remark;
}