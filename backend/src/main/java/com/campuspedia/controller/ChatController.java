package com.campuspedia.controller;

import com.campuspedia.rag.RagChain;
import com.campuspedia.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RagChain ragChain;
    private final AuthHelper authHelper;

    public ChatController(RagChain ragChain, AuthHelper authHelper) {
        this.ragChain = ragChain;
        this.authHelper = authHelper;
    }

    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String question = String.valueOf(body.getOrDefault("question", ""));
        Long sessionId = body.get("sessionId") == null ? null : Long.valueOf(String.valueOf(body.get("sessionId")));
        Long userId = authHelper.currentUserId(request);
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> ragChain.stream(question, sessionId, userId, emitter));
        return emitter;
    }
}
