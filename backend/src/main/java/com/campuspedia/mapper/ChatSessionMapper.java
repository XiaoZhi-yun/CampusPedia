package com.campuspedia.mapper;

/**
 * 会话模块数据访问层文件，对应 t_chat_session（用户一次多轮对话）。
 * 所属 mapper 层：会话列表按 user_id 与 update_time 倒序由 Service 层拼接条件，无自定义 SQL。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 继承 MyBatis-Plus BaseMapper 提供 t_chat_session 单表 CRUD，无自定义 SQL。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {}
