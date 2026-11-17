package com.campuspedia.controller;
/**
 * 控制层：问答会话的增删改查，挂载于 /api/sessions。
 * <p>所有操作按当前用户隔离并经 ownedSession 校验归属，防止越权读取他人会话。</p>
 */

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.ChatMessage;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.security.AuthHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话接口：当前用户的会话增删改查与消息列表。
 * <p>所有按 id 的操作先经 ownedSession 校验归属，避免跨用户访问。</p>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AuthHelper authHelper;

    /**
     * 注入会话/消息 Mapper 与鉴权辅助。
     *
     * @param sessionMapper 会话表读写
     * @param messageMapper 会话消息读写
     * @param authHelper    解析当前登录用户 id
     */
    public SessionController(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper, AuthHelper authHelper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.authHelper = authHelper;
    }

    private Long uid(HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        return userId;
    }

    /** 校验会话归属，防止越权读取、改名或删除他人会话。 */
    private ChatSession ownedSession(Long id, Long userId) {
        ChatSession s = sessionMapper.selectById(id);
        if (s == null) throw new BizException("会话不存在");
        if (!userId.equals(s.getUserId())) throw new BizException(403, "无权访问该会话");
        return s;
    }

    /**
     * 仅返回属于当前用户的会话，按更新时间倒序。
     * <p>以用户 id 作为查询条件，避免泄露他人会话列表。</p>
     *
     * @param request 用于解析当前用户 id
     * @return 当前用户的会话列表
     */
    @GetMapping
    public Result<List<ChatSession>> list(HttpServletRequest request) {
        Long userId = uid(request);
        return Result.ok(sessionMapper.selectList(new QueryWrapper<ChatSession>().eq("user_id", userId).orderByDesc("update_time")));
    }

    /**
     * 为当前用户新建会话，标题缺省为「新对话」，消息数初始化为 0。
     * <p>副作用是插入一条会话记录；会话归属强制取自登录态而非请求体。</p>
     *
     * @param body    含可选 title 的请求体
     * @param request 用于解析当前用户 id
     * @return 新建的会话
     */
    @PostMapping
    public Result<ChatSession> create(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = uid(request);
        ChatSession s = new ChatSession();
        s.setUserId(userId);
        s.setTitle(body.getOrDefault("title", "新对话"));
        s.setMessageCount(0);
        sessionMapper.insert(s);
        return Result.ok(s);
    }

    /**
     * 返回指定会话的消息列表；先校验会话归属，越权抛 403。
     *
     * @param id      会话主键
     * @param request 用于解析当前用户 id
     * @return 按 id 升序的消息列表
     */
    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id, HttpServletRequest request) {
        ownedSession(id, uid(request));
        return Result.ok(messageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", id).orderByAsc("id")));
    }

    /**
     * 重命名指定会话：校验归属后仅在标题非空时更新。
     * <p>副作用是更新一条会话记录；越权抛 403。</p>
     *
     * @param id      会话主键
     * @param body    含 title 的请求体
     * @param request 用于解析当前用户 id
     * @return 无 data 的成功响应
     */
    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        ChatSession s = ownedSession(id, uid(request));
        if (body.get("title") != null && !body.get("title").isBlank()) s.setTitle(body.get("title"));
        sessionMapper.updateById(s);
        return Result.ok(null);
    }

    /**
     * 删除指定会话：校验归属后在事务内先删消息再删会话，避免残留孤儿消息。
     * <p>副作用是删除多条消息与一条会话；越权抛 403。</p>
     *
     * @param id      会话主键
     * @param request 用于解析当前用户 id
     * @return 无 data 的成功响应
     */
    @Transactional
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        ownedSession(id, uid(request));
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", id));
        sessionMapper.deleteById(id);
        return Result.ok(null);
    }
}
