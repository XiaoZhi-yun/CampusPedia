package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.ChatMessage;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.security.AuthHelper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AuthHelper authHelper;

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

    @GetMapping
    public Result<List<ChatSession>> list(HttpServletRequest request) {
        Long userId = uid(request);
        return Result.ok(sessionMapper.selectList(new QueryWrapper<ChatSession>().eq("user_id", userId).orderByDesc("update_time")));
    }

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

    @GetMapping("/{id}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable Long id, HttpServletRequest request) {
        uid(request);
        return Result.ok(messageMapper.selectList(new QueryWrapper<ChatMessage>().eq("session_id", id).orderByAsc("id")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        uid(request);
        messageMapper.delete(new QueryWrapper<ChatMessage>().eq("session_id", id));
        sessionMapper.deleteById(id);
        return Result.ok(null);
    }
}
