package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS count FROM t_chat_message WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String, Object>> countByDay();
}
