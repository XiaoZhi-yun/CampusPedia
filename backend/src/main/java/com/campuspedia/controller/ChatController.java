package com.campuspedia.controller;

import com.campuspedia.domain.ChatRequest;
import com.campuspedia.domain.ChatResponse;
import com.campuspedia.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 问答 REST 接口。 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 提问并返回带溯源的答案。
     *
     * @param request 提问请求（question）
     * @return {answer, sources[]}
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        try {
            return ResponseEntity.ok(chatService.answer(request.question()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 流式问答（SSE）：事件依次为 sources → delta* → done。
     */
    @PostMapping("/chat/stream")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> chatService.streamAnswer(request.question(), emitter));
        return emitter;
    }
}
