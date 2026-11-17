package com.campuspedia.mapper;

/**
 * 消息模块数据访问层文件，对应 t_chat_message（提问与回答及生成统计）。
 * 所属 mapper 层：除 BaseMapper CRUD 外，另有近 7 日趋势与热门问题两组聚合统计。
 */
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 继承 MyBatis-Plus BaseMapper 提供 t_chat_message 单表 CRUD，另提供近 7 日趋势与热门问题聚合。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 统计近 7 日每日消息量，供 StatsController 的趋势图使用。
     * 时间口径 create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)，按 DATE(create_time) 分组；
     * COUNT(*) 含 user 与 assistant 全部消息（非仅提问数），依赖 idx_create_time；
     * 无入参，返回按 day 升序的 {day, count} 列表。
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS count FROM t_chat_message WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(create_time) ORDER BY day")
    List<Map<String, Object>> countByDay();

    /**
     * 统计热门提问 Top10，供 StatsController 展示高频问题。
     * 仅取 role='user' 的消息，按 content（提问原文）分组 COUNT(*) 后降序取前 10；
     * 口径为原文完全相同才合并计数，未做文本归一化；无入参，返回 {question, count} 列表。
     */
    @Select("SELECT content AS question, COUNT(*) AS count FROM t_chat_message WHERE role = 'user' GROUP BY content ORDER BY count DESC LIMIT 10")
    List<Map<String, Object>> topQuestions();
}
