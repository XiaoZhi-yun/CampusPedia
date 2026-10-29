package com.campuspedia.rag;

import com.campuspedia.entity.ChatMessage;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.service.ConfigService;
import com.campuspedia.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RagChain {

    private static final Logger log = LoggerFactory.getLogger(RagChain.class);

    private static final Set<String> GREETINGS = Set.of(
            "你好", "您好", "嗨", "hi", "hello", "哈喽", "在吗", "在不在",
            "早上好", "下午好", "晚上好", "中午好", "谢谢", "感谢", "再见", "拜拜");

    private final QueryRewriter queryRewriter;
    private final HybridRetriever hybridRetriever;
    private final RerankService rerankService;
    private final LlmService llmService;
    private final ConfigService configService;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;

    public RagChain(QueryRewriter queryRewriter, HybridRetriever hybridRetriever,
                    RerankService rerankService, LlmService llmService,
                    ConfigService configService, ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper) {
        this.queryRewriter = queryRewriter;
        this.hybridRetriever = hybridRetriever;
        this.rerankService = rerankService;
        this.llmService = llmService;
        this.configService = configService;
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
    }

    public void stream(String question, List<String> history, Long sessionId, Long userId, SseEmitter emitter) {
        try {
            int topKRecall = configService.getInt("rag_top_k_recall", 20);
            int topNFinal = configService.getInt("rag_top_n_final", 5);
            boolean rerankEnabled = configService.getBool("rerank_enabled", true);
            boolean hybridEnabled = configService.getBool("hybrid_enabled", true);
            boolean rewriteEnabled = configService.getBool("query_rewrite_enabled", true);
            String systemPrompt = configService.get("system_prompt",
                    "你是学事通CampusPedia，只依据参考资料回答，资料不足明说，结论后标[1][2]");

            String rewriteQuery = rewriteEnabled ? queryRewriter.rewrite(question, history) : question;

            if (isGreeting(question)) {
                emit(emitter, "sources", List.of());
                emit(emitter, "trace", Map.of("note", "问候语，无需检索"));
                StringBuilder greet = new StringBuilder();
                llmService.stream(systemPrompt, "用户打了个招呼，请简短友好地回应，不超过一句话。", delta -> { greet.append(delta); emit(emitter, "delta", delta); });
                emit(emitter, "done", null);
                emitter.complete();
                saveMessage(sessionId, userId, question, question, greet.toString(), List.of());
                return;
            }

            double threshold = Double.parseDouble(configService.get("rag_score_threshold", "0.3"));
            List<RetrievedChunk> candidates = hybridRetriever.retrieve(rewriteQuery, topKRecall, hybridEnabled);
            List<RetrievedChunk> finalHits;
            if (rerankEnabled && candidates.size() > 1) {
                try {
                    finalHits = rerank(candidates, question, topNFinal, threshold);
                } catch (Exception e) {
                    log.warn("Rerank 失败，降级为混合检索：{}", e.getMessage());
                    finalHits = new ArrayList<>(candidates.subList(0, Math.min(topNFinal, candidates.size())));
                }
            } else {
                finalHits = new ArrayList<>(candidates.subList(0, Math.min(topNFinal, candidates.size())));
            }
            if (finalHits.isEmpty()) finalHits = new ArrayList<>(candidates.subList(0, Math.min(1, candidates.size())));

            List<Map<String, Object>> sources = buildSources(finalHits);
            String userPrompt = buildUserPrompt(question, finalHits);

            emit(emitter, "sources", sources);
            emit(emitter, "trace", Map.of(
                    "rewriteQuery", rewriteQuery,
                    "candidateCount", candidates.size(),
                    "finalCount", finalHits.size(),
                    "threshold", threshold));

            StringBuilder answer = new StringBuilder();
            llmService.stream(systemPrompt, userPrompt, delta -> {
                answer.append(delta);
                emit(emitter, "delta", delta);
            });

            emit(emitter, "done", null);
            emitter.complete();

            saveMessage(sessionId, userId, question, rewriteQuery, answer.toString(), sources);
        } catch (Exception e) {
            emit(emitter, "error", e.getMessage());
            emitter.complete();
        }
    }

    private List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String query, int topN, double threshold) {
        List<String> docs = candidates.stream().map(RetrievedChunk::content).toList();
        List<RerankResult> results = rerankService.rerank(query, docs);
        List<RetrievedChunk> finalHits = new ArrayList<>();
        for (RerankResult r : results) {
            if (r.relevanceScore() >= threshold && r.index() < candidates.size()) {
                RetrievedChunk c = candidates.get(r.index());
                finalHits.add(new RetrievedChunk(c.vectorId(), c.content(), c.documentId(), c.fileName(),
                        c.category(), c.clauseRef(), c.sourceUrl(), r.relevanceScore()));
            }
            if (finalHits.size() >= topN) break;
        }
        return finalHits;
    }

    private List<Map<String, Object>> buildSources(List<RetrievedChunk> hits) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk c = hits.get(i);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("index", i + 1);
            s.put("fileName", c.fileName());
            s.put("category", c.category());
            s.put("clauseRef", c.clauseRef());
            String snip = c.content();
            s.put("snippet", snip.length() > 180 ? snip.substring(0, 180) + "..." : snip);
            s.put("sourceUrl", c.sourceUrl());
            s.put("score", Math.round(c.score() * 10000) / 10000.0);
            sources.add(s);
        }
        return sources;
    }

    private String buildUserPrompt(String question, List<RetrievedChunk> hits) {
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk c = hits.get(i);
            sb.append("[").append(i + 1).append("] 《").append(c.fileName()).append("》")
              .append(c.clauseRef()).append("：").append(c.content()).append("\n\n");
        }
        sb.append("【学生问题】\n").append(question).append("\n\n请依据上述资料回答，结论后标注 [来源N]。");
        return sb.toString();
    }

    private boolean isGreeting(String q) {
        if (q == null) return false;
        String s = q.trim().toLowerCase();
        return GREETINGS.contains(s);
    }

    private void saveMessage(Long sessionId, Long userId, String question, String rewriteQuery, String answer, List<Map<String, Object>> sources) {
        if (userId == null) return; // 游客：临时会话，不落库
        // 会话标题：首条消息用问题内容替换默认"新对话"
        if (sessionId != null) {
            try {
                ChatSession session = sessionMapper.selectById(sessionId);
                if (session != null && (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle()))) {
                    String t = question == null ? "" : question.trim();
                    session.setTitle(t.length() > 20 ? t.substring(0, 20) : t);
                    sessionMapper.updateById(session);
                }
            } catch (Exception ignore) {}
        }
        try {
            ChatMessage m = new ChatMessage();
            m.setSessionId(sessionId == null ? 0L : sessionId);
            m.setUserId(userId == null ? 0L : userId);
            m.setRole("user");
            m.setContent(question);
            m.setRetrieveQuery(rewriteQuery);
            messageMapper.insert(m);
            ChatMessage a = new ChatMessage();
            a.setSessionId(sessionId == null ? 0L : sessionId);
            a.setUserId(userId == null ? 0L : userId);
            a.setRole("assistant");
            a.setContent(answer);
            a.setSources(sources.toString());
            a.setHitCount(sources.size());
            messageMapper.insert(a);
        } catch (Exception ignore) {}
    }

    private void emit(SseEmitter emitter, String type, Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        if (data != null) m.put("data", data);
        try { emitter.send(SseEmitter.event().data(m)); } catch (IOException ignored) {}
    }
}
