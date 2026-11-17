package com.campuspedia.entity;

/**
 * 系统配置表：RAG 参数与系统提示词，键值对。
 * 对应数据表 t_sys_config，所属 entity 层，由 SysConfigMapper 映射。
 * 以 configKey 作业务主键（非自增），与其余表 IdType.AUTO 的主键策略不同。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_sys_config 实体。 */
@Data
@TableName("t_sys_config")
public class SysConfig {
    /** 配置键，业务主键（非自增），故用 @TableId 而非 IdType.AUTO；按此等值读取 RAG 参数与提示词。 */
    @TableId
    private String configKey;
    /** 配置值，可空。 */
    private String configValue;
    /** 配置说明，可空。 */
    private String remark;
}