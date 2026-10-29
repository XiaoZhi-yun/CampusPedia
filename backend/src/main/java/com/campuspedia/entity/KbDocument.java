package com.campuspedia.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_kb_document 实体。 */
@Data
@TableName("t_kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String fileName;
    private String fileType;
    private Integer fileSize;
    private String filePath;
    private String sourceUrl;
    private String vectorStatus;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}