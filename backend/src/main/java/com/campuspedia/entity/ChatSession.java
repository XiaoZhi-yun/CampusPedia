package com.campuspedia.entity;

/**
 * 会话表：用户的一次多轮对话。
 * 对应数据表 t_chat_session，所属 entity 层，由 ChatSessionMapper 映射。
 * user_id 逻辑关联 t_user.id，全库无物理外键；update_time 由库 ON UPDATE 维护，会话列表按此倒序展示。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_chat_session 实体。 */
@Data
@TableName("t_chat_session")
public class ChatSession {
    /** 会话ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属用户ID，逻辑关联 t_user.id，无物理外键；删用户时由应用层据此级联删会话。 */
    private Long userId;
    /** 会话标题，库有默认值（新会话）。 */
    private String title;
    /** 消息条数，库默认 0，由应用层维护并与 t_chat_message 条数保持一致。 */
    private Integer messageCount;
    /** 创建时间，库默认 CURRENT_TIMESTAMP。 */
    private LocalDateTime createTime;
    /** 最后活动时间，库 ON UPDATE 自动维护；会话列表按此倒序（idx_user_update）。 */
    private LocalDateTime updateTime;
}