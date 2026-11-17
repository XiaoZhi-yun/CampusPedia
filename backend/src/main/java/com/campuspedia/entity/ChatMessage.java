package com.campuspedia.entity;

/**
 * 消息表：会话内的提问与回答，含检索与生成统计。
 * 对应数据表 t_chat_message，所属 entity 层，由 ChatMessageMapper 映射。
 * session_id / user_id 分别逻辑关联 t_chat_session.id / t_user.id，全库无物理外键；
 * create_time 支撑近 7 日趋势、热门问题与历史清理。
 */
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/** t_chat_message 实体。 */
@Data
@TableName("t_chat_message")
public class ChatMessage {
    /** 消息ID，主键；IdType.AUTO 由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属会话ID，逻辑关联 t_chat_session.id，无物理外键。 */
    private Long sessionId;
    /** 所属用户ID，逻辑关联 t_user.id，无物理外键；删用户时据此级联删消息。 */
    private Long userId;
    /** 消息角色：user 用户提问 / assistant 助手回答；热门问题统计只取 role='user'。 */
    private String role;
    /** 消息正文；topQuestions 按 content 聚合统计高频提问。 */
    private String content;
    /** 参考来源，JSON 文本，记录本次回答引用的切片。 */
    private String sources;
    /** 改写后的检索 query，库默认空串，用于复盘 RAG 检索效果。 */
    private String retrieveQuery;
    /** 命中切片数，库默认 0。 */
    private Integer hitCount;
    /** 生成耗时，单位毫秒，库默认 0。 */
    private Integer durationMs;
    /** 消耗 token 数，库默认 0。 */
    private Integer tokenCount;
    /** 消息时间，库默认 CURRENT_TIMESTAMP；按 DATE 聚合支撑近 7 日趋势与历史清理。 */
    private LocalDateTime createTime;
}