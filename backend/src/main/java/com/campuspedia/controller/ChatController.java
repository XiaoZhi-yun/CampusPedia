package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.rag.RagChain;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.entity.SysLog;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.mapper.SysLogMapper;
import com.campuspedia.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final RagChain ragChain;
    private final AuthHelper authHelper;
    private final SysLogMapper sysLogMapper;
    private final ChatSessionMapper sessionMapper;

    public ChatController(RagChain ragChain, AuthHelper authHelper, SysLogMapper sysLogMapper, ChatSessionMapper sessionMapper) {
        this.ragChain = ragChain;
        this.authHelper = authHelper;
        this.sysLogMapper = sysLogMapper;
        this.sessionMapper = sessionMapper;
    }

    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        Long sessionId = parseId(body.get("sessionId"));
        Long userId = authHelper.currentUserId(request);

        // 前端传入的 sessionId 不可信，必须校验归属，防止向他人会话注入消息
        if (sessionId != null) {
            if (userId == null) throw new BizException(401, "未登录");
            ChatSession s = sessionMapper.selectById(sessionId);
            if (s == null || !userId.equals(s.getUserId())) throw new BizException(403, "无权访问该会话");
        }

        try {
            SysLog l = new SysLog();
            l.setUsername(userId == null ? "未知用户" : String.valueOf(userId));
            l.setRole("user");
            l.setAction("chat");
            l.setDetail(question.length() > 200 ? question.substring(0, 200) : question);
            l.setIp(request.getRemoteAddr());
            sysLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("写入问答日志失败", e);
        }
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> ragChain.stream(question, parseHistory(body.get("history")), sessionId, userId, emitter));
        return emitter;
    }

    private Long parseId(Object v) {
        if (v == null) return null;
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> parseHistory(Object historyObj) {
        List<String> history = new ArrayList<>();
        if (historyObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String role = String.valueOf(m.get("role"));
                    String content = String.valueOf(m.get("content"));
                    history.add(("user".equals(role) ? "用户：" : "助手：") + content);
                }
            }
        }
        return history;
    }
}
