package com.campuspedia.service;

import com.campuspedia.domain.ChatResponse;
import com.campuspedia.domain.Source;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答编排：检索 → Prompt 组装 → 调用 LLM → 组装溯源引用。
 */
@Service
public class ChatService {

    private static final int TOP_K = 6;
    private static final double THRESHOLD = 0.5;

    private static final List<String> GREETINGS = List.of("你好", "您好", "hi", "hello", "在吗", "在么", "嗨");

    private final Retriever retriever;
    private final PromptBuilder promptBuilder;
    private final LlmService llmService;

    public ChatService(Retriever retriever, PromptBuilder promptBuilder, LlmService llmService) {
        this.retriever = retriever;
        this.promptBuilder = promptBuilder;
        this.llmService = llmService;
    }

    public ChatResponse answer(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        List<RetrievalResult> results = retriever.retrieve(question, TOP_K, THRESHOLD);
        if (results.isEmpty()) {
            return new ChatResponse(isGreeting(question) ? greetingText() : fallbackText(), List.of());
        }
        String answer = llmService.chat(promptBuilder.systemPrompt(), promptBuilder.userPrompt(question, results));
        return new ChatResponse(answer, buildSources(results));
    }

    /**
     * 流式问答：先发 sources，再逐段发 delta，最后 done。
     */
    public void streamAnswer(String question, SseEmitter emitter) {
        try {
            List<RetrievalResult> results = (question == null || question.isBlank())
                    ? List.of()
                    : retriever.retrieve(question, TOP_K, THRESHOLD);
            emit(emitter, "sources", buildSources(results));
            if (results.isEmpty()) {
                emit(emitter, "delta", isGreeting(question) ? greetingText() : fallbackText());
            } else {
                String system = promptBuilder.systemPrompt();
                String user = promptBuilder.userPrompt(question, results);
                // 思考可视化：透传组装后的 Prompt 与检索参数
                emit(emitter, "trace", Map.of(
                        "topK", TOP_K,
                        "threshold", THRESHOLD,
                        "systemPrompt", system,
                        "userPrompt", user
                ));
                llmService.stream(system, user, delta -> emit(emitter, "delta", delta));
            }
            emit(emitter, "done", null);
            emitter.complete();
        } catch (Exception e) {
            emit(emitter, "error", e.getMessage());
            emitter.complete();
        }
    }

    private boolean isGreeting(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        String t = q.trim().toLowerCase();
        return GREETINGS.stream().anyMatch(t::contains);
    }

    private String greetingText() {
        return "你好！我是「学事通」CampusPedia，校园学生事务智能问答助手。\n你可以问我关于学籍、选课考试、奖惩、奖助学金、勤工助学、住宿、素质拓展、就业、医保等问题。";
    }

    private String fallbackText() {
        return "根据现有制度文件暂未找到明确依据，建议咨询相关部门。";
    }

    private void emit(SseEmitter emitter, String type, Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        if (data != null) {
            m.put("data", data);
        }
        try {
            emitter.send(SseEmitter.event().data(m));
        } catch (IOException ignored) {
            // 客户端断开，忽略
        }
    }

    private List<Source> buildSources(List<RetrievalResult> results) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            RetrievalResult r = results.get(i);
            String snippet = r.chunk().text();
            if (snippet.length() > 120) {
                snippet = snippet.substring(0, 120) + "...";
            }
            sources.add(new Source(
                    i + 1,
                    r.chunk().fileName(),
                    r.chunk().category(),
                    r.chunk().clauseRef(),
                    snippet,
                    Math.round(r.similarity() * 10000) / 10000.0,
                    r.chunk().sourceUrl()
            ));
        }
        return sources;
    }
}
