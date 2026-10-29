package com.campuspedia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_faq 实体。 */
@Data
@TableName("t_faq")
public class Faq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String question;
    private String answer;
    private Long categoryId;
    private Integer hitCount;
}