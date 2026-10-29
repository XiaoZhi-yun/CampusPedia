package com.campuspedia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_kb_category 实体。 */
@Data
@TableName("t_kb_category")
public class KbCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String remark;
    private Integer sortNum;
}