package com.campuspedia.controller;
/**
 * 控制层：运营统计数据的只读接口，挂载于 /api/stats。
 * <p>数据由各 Mapper 的聚合查询直接提供，除 topQuestions 外均由 SecurityConfig 限定管理员访问。</p>
 */

import com.campuspedia.common.Result;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.mapper.KbDocumentMapper;
import com.campuspedia.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计接口：汇总文档、会话、消息、用户数量及按类型/日期的分布。
 * <p>结果直接来自各 Mapper 的聚合查询，供管理端概览页使用。</p>
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final KbDocumentMapper documentMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final UserMapper userMapper;

    /**
     * 注入文档、会话、消息、用户四个 Mapper 用于聚合统计。
     *
     * @param documentMapper 文档数量与类型分布
     * @param sessionMapper  会话数量
     * @param messageMapper  消息数量、热门问题与按日统计
     * @param userMapper     用户数量
     */
    public StatsController(KbDocumentMapper documentMapper, ChatSessionMapper sessionMapper,
                           ChatMessageMapper messageMapper, UserMapper userMapper) {
        this.documentMapper = documentMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
    }

    /**
     * 汇总文档、会话、消息、用户四类总数，供管理端概览卡片展示。
     *
     * @return 含 documents/sessions/messages/users 计数的 Map
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("documents", documentMapper.selectCount(null));
        m.put("sessions", sessionMapper.selectCount(null));
        m.put("messages", messageMapper.selectCount(null));
        m.put("users", userMapper.selectCount(null));
        return Result.ok(m);
    }

    /**
     * 按文档类型统计数量，供管理端展示语料构成。
     *
     * @return 类型与数量的聚合结果列表
     */
    @GetMapping("/docTypes")
    public Result<List<Map<String, Object>>> docTypes() {
        return Result.ok(documentMapper.countByType());
    }

    /**
     * 返回高频提问，是 /api/stats 下唯一对登录用户开放、不限管理员的接口。
     *
     * @return 热门问题聚合结果列表
     */
    @GetMapping("/topQuestions")
    public Result<List<Map<String, Object>>> topQuestions() {
        return Result.ok(messageMapper.topQuestions());
    }

    /**
     * 按日统计消息量，供管理端绘制趋势。
     *
     * @return 按日的消息数量列表
     */
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily() {
        return Result.ok(messageMapper.countByDay());
    }
}
