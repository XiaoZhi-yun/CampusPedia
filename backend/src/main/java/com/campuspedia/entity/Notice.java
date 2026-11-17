package com.campuspedia.entity;

/**
 * 公告表：系统公告，置顶优先展示。
 * 对应数据表 t_notice，所属 entity 层，由 NoticeMapper 映射。
 * 列表按 top 降序、create_time 升序排列，对应 idx_top_create 联合索引。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_notice 实体。 */
@Data
@TableName("t_notice")
public class Notice {
    /** 公告ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 公告标题。 */
    private String title;
    /** 公告正文。 */
    private String content;
    /** 是否置顶：1 是 / 0 否，库默认 0；列表按 top 降序、create_time 升序。 */
    private Integer top;
    /** 发布时间，库默认 CURRENT_TIMESTAMP。 */
    private LocalDateTime createTime;
}