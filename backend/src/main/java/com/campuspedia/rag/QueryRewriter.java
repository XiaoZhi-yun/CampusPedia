package com.campuspedia.rag;

import com.campuspedia.service.LlmService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QueryRewriter {

    private final LlmService llmService;

    public QueryRewriter(LlmService llmService) {
        this.llmService = llmService;
    }

    public String rewrite(String question, List<String> history) {
        if (question == null || question.length() > 30 || history == null || history.isEmpty()) {
            return question;
        }
        String system = "你是查询改写助手。结合对话历史，把用户当前问题改写成独立完整、可直接检索的问题。只输出改写后的问题，不要解释。";
        String user = "历史对话：\n" + String.join("\n", history) + "\n当前问题：" + question;
        try {
            String rewritten = llmService.chat(system, user);
            return (rewritten == null || rewritten.isBlank()) ? question : rewritten.trim();
        } catch (Exception e) {
            return question;
        }
    }
}
