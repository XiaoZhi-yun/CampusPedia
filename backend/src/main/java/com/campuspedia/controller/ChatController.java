package com.campuspedia.controller;

import com.campuspedia.rag.RagChain;
import com.campuspedia.entity.SysLog;
import com.campuspedia.mapper.SysLogMapper;
import com.campuspedia.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
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

    private final RagChain ragChain;
    private final AuthHelper authHelper;
    private final SysLogMapper sysLogMapper;

    public ChatController(RagChain ragChain, AuthHelper authHelper, SysLogMapper sysLogMapper) {
        this.ragChain = ragChain;
        this.authHelper = authHelper;
        this.sysLogMapper = sysLogMapper;
    }

    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        Long sessionId = body.get("sessionId") == null ? null : Long.valueOf(String.valueOf(body.get("sessionId")));
        Long userId = authHelper.currentUserId(request);
        try {
            SysLog l = new SysLog();
            l.setUsername(userId == null ? "游客" : String.valueOf(userId));
            l.setRole("user");
            l.setAction("chat");
            l.setDetail(question.length() > 200 ? question.substring(0, 200) : question);
            l.setIp(request.getRemoteAddr());
            sysLogMapper.insert(l);
        } catch (Exception ignore) {}
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> ragChain.stream(question, parseHistory(body.get("history")), sessionId, userId, emitter));
        return emitter;
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
