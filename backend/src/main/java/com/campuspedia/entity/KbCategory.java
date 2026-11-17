package com.campuspedia.entity;

/**
 * 语料分类表：上传文档时的归类，含「其他」兜底分类。
 * 对应数据表 t_kb_category，所属 entity 层，由 KbCategoryMapper 映射。
 * t_kb_document.category_id 逻辑关联本表 id（0 表示未分类），全库无物理外键，由应用层约束。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** t_kb_category 实体。 */
@Data
@TableName("t_kb_category")
public class KbCategory {
    /** 分类ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 分类名称。 */
    private String name;
    /** 分类说明，库默认空串。 */
    private String remark;
    /** 排序号，升序展示，库默认 0；分类行少，列表全表扫描排序即可。 */
    private Integer sortNum;
}