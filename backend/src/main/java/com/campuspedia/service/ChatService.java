package com.campuspedia.service;

import com.campuspedia.domain.ChatResponse;
import com.campuspedia.domain.Source;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 问答编排：检索 → Prompt 组装 → 调用 LLM → 组装溯源引用。
 */
@Service
public class ChatService {

    private static final int TOP_K = 6;
    private static final double THRESHOLD = 0.5;

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
            return new ChatResponse("根据现有制度文件暂未找到明确依据，建议咨询相关部门。", List.of());
        }
        String answer = llmService.chat(promptBuilder.systemPrompt(), promptBuilder.userPrompt(question, results));
        return new ChatResponse(answer, buildSources(results));
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
                    Math.round(r.similarity() * 10000) / 10000.0
            ));
        }
        return sources;
    }
}
