package com.campuspedia.rag;
/**
 * RAG 问答主链路编排，隶属 RAG 检索链路层（rag 包），是流式问答的入口。
 *
 * <p>上游：聊天 Controller 通过 {@code SseEmitter} 调用；下游：串联 {@code QueryRewriter} 改写、
 * {@code HybridRetriever} 混合检索、{@code RerankService} 重排、{@code LlmService} 流式生成，
 * 并借 {@code ConfigService} 读取开关、{@code ChatMessageMapper}/{@code ChatSessionMapper} 落库。
 * 负责 SSE 事件推送、客户端断开中断与消息持久化。</p>
 */

import com.campuspedia.entity.ChatMessage;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.service.ConfigService;
import com.campuspedia.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 编排「改写 → 检索 → 重排 → 生成 → 落库」的流式问答主链路。
 *
 * <p>链路各阶段均可配置开关且失败有降级：重排不可用退回混合检索结果，向量/关键词任一路失败
 * 由 {@code HybridRetriever} 兜底。SSE 推送与落库受每请求独立的 {@code alive} 标志控制，
 * 客户端断开即中止生成且不写库。</p>
 */
@Component
public class RagChain {

    private static final Logger log = LoggerFactory.getLogger(RagChain.class);

    /** 常见问候语集合；命中时跳过检索直接寒暄，省去无意义的召回与 token。 */
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 组装问答链路的全部依赖。
     *
     * @param queryRewriter   多轮查询改写
     * @param hybridRetriever 混合检索
     * @param rerankService   候选重排
     * @param llmService      流式答案生成
     * @param configService   召回条数、阈值、开关与系统提示词
     * @param messageMapper   问答消息落库
     * @param sessionMapper   会话标题与消息数更新
     */
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

    /**
     * 执行一次流式问答，按 sources / trace / delta / done（或 error）顺序推送 SSE 事件。
     *
     * <p>副作用：生成完成后写入用户与助手两条消息并更新会话；未登录（{@code userId} 为
     * {@code null}）或客户端断开时不落库。异常且连接仍在时推送 error 后结束 emitter，
     * 连接已断则静默收尾。</p>
     *
     * @param question  当前用户问题
     * @param history   历史对话，供查询改写使用
     * @param sessionId 会话 id，为空时消息归到 0
     * @param userId    用户 id，为空表示未登录、不落库
     * @param emitter   本次请求的 SSE 发射器，方法内负责 complete
     */
    public void stream(String question, List<String> history, Long sessionId, Long userId, SseEmitter emitter) {
        // 客户端断开后立刻停止生成与落库，避免继续消耗 LLM token 并写入用户已「停止」的答案
        AtomicBoolean alive = new AtomicBoolean(true);
        try {
            // 召回/截断/阈值/开关均取自 t_sys_config，缺省值保证无配置也能跑通链路
            int topKRecall = configService.getInt("rag_top_k_recall", 20);
            int topNFinal = configService.getInt("rag_top_n_final", 5);
            boolean rerankEnabled = configService.getBool("rerank_enabled", true);
            boolean hybridEnabled = configService.getBool("hybrid_enabled", true);
            boolean rewriteEnabled = configService.getBool("query_rewrite_enabled", true);
            // 系统提示词可运营覆盖，缺省约束模型只依据资料作答并单次标注来源
            String systemPrompt = configService.get("system_prompt",
                    "你是学事通CampusPedia，只依据参考资料回答，资料不足就明说。引用资料时，只在首次引用该来源处标注一次 [来源N]，不要每句话都重复标注。");

            String rewriteQuery = rewriteEnabled ? queryRewriter.rewrite(question, history) : question;

            if (isGreeting(question)) {
                emit(emitter, "sources", List.of(), alive);
                emit(emitter, "trace", Map.of("note", "问候语，无需检索"), alive);
                StringBuilder greet = new StringBuilder();
                llmService.stream(systemPrompt, "用户打了个招呼，请简短友好地回应，不超过一句话。",
                        delta -> { greet.append(delta); emit(emitter, "delta", delta, alive); });
                emit(emitter, "done", null, alive);
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
                    // 重排服务不可用/无密钥时退回纯混合检索的前 topN，问答不中断
                    log.warn("Rerank 失败，降级为混合检索：{}", e.getMessage());
                    finalHits = new ArrayList<>(candidates.subList(0, Math.min(topNFinal, candidates.size())));
                }
            } else {
                finalHits = new ArrayList<>(candidates.subList(0, Math.min(topNFinal, candidates.size())));
            }
            if (finalHits.isEmpty()) finalHits = new ArrayList<>(candidates.subList(0, Math.min(1, candidates.size())));

            // 同一篇文档的多个切片合并为一个来源编号，避免一篇文档被拆成 [来源1]~[来源5]
            List<SourceGroup> groups = groupByDocument(finalHits);
            List<Map<String, Object>> sources = buildSources(groups);
            // 编号与 sources 事件一一对应：Prompt 与来源卡片共用同一分组的 index
            String userPrompt = buildUserPrompt(question, groups);

            emit(emitter, "sources", sources, alive);
            emit(emitter, "trace", Map.of(
                    "rewriteQuery", rewriteQuery,
                    "candidateCount", candidates.size(),
                    "finalCount", finalHits.size(),
                    "sourceCount", groups.size(),
                    "threshold", threshold), alive);

            StringBuilder answer = new StringBuilder();
            llmService.stream(systemPrompt, userPrompt, delta -> {
                answer.append(delta);
                emit(emitter, "delta", delta, alive);
            });

            emit(emitter, "done", null, alive);
            emitter.complete();
            // 仅在 emitter.complete 之后落库；此前任一 emit 抛错都会跳过本行
            // 落库前兜底去重：模型仍可能重复标注 [来源N]，历史消息只保留首次引用
            saveMessage(sessionId, userId, question, rewriteQuery, dedupeCitations(answer.toString()), sources);
        } catch (Exception e) {
            if (alive.get()) {
                try {
                    emit(emitter, "error", "生成失败，请稍后重试", alive);
                    emitter.complete();
                } catch (Exception ignored) {
                    // 连接已断，无需再推送
                }
                log.warn("RAG 链路异常", e);
            }
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

    /**
     * 同一篇文档的多个切片归为一个来源分组。
     * 来源卡片语义是「引用到具体文件」而非「引用到第几个切片」；不合并会把一篇文档
     * 拆成 [来源1]~[来源5]，答案里随之出现大量重复编号。
     */
    private List<SourceGroup> groupByDocument(List<RetrievedChunk> hits) {
        Map<Long, SourceGroup> byDoc = new LinkedHashMap<>();
        for (RetrievedChunk c : hits) {
            SourceGroup g = byDoc.get(c.documentId());
            if (g == null) {
                g = new SourceGroup(c);
                byDoc.put(c.documentId(), g);
            } else {
                if ((g.clauseRef == null || g.clauseRef.isEmpty())
                        && c.clauseRef() != null && !c.clauseRef().isEmpty()) g.clauseRef = c.clauseRef();
                g.score = Math.max(g.score, c.score());
                g.content.append('\n');
            }
            g.content.append(c.content());
        }
        List<SourceGroup> groups = new ArrayList<>(byDoc.values());
        for (int i = 0; i < groups.size(); i++) groups.get(i).index = i + 1;
        return groups;
    }

    /** 一个来源分组对应一篇文档；index 同时用于 Prompt 标注与来源卡片编号。 */
    private static class SourceGroup {
        int index;
        final long documentId;
        final String fileName;
        final String category;
        String clauseRef;
        final String sourceUrl;
        double score;
        final StringBuilder content = new StringBuilder();

        SourceGroup(RetrievedChunk c) {
            this.documentId = c.documentId();
            this.fileName = c.fileName();
            this.category = c.category();
            this.clauseRef = c.clauseRef();
            this.sourceUrl = c.sourceUrl();
            this.score = c.score();
        }
    }

    private List<Map<String, Object>> buildSources(List<SourceGroup> groups) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (SourceGroup g : groups) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("index", g.index);
            s.put("fileName", g.fileName);
            s.put("category", g.category);
            s.put("clauseRef", g.clauseRef);
            s.put("snippet", truncate(g.content.toString(), 300));
            s.put("sourceUrl", safeUrl(g.sourceUrl));
            s.put("score", Math.round(g.score * 10000) / 10000.0);
            sources.add(s);
        }
        return sources;
    }

    /** 来源链接只允许 http/https，阻断 javascript: 等协议。 */
    private String safeUrl(String url) {
        return url != null && url.matches("(?i)^https?://.*") ? url : "";
    }

    private String buildUserPrompt(String question, List<SourceGroup> groups) {
        // 资料在前、问题在后；一个编号对应一篇文档，避免模型把同一文件标成多个来源
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        for (SourceGroup g : groups) {
            sb.append("[").append(g.index).append("] 《").append(g.fileName).append("》")
              .append(g.clauseRef == null ? "" : g.clauseRef).append("：")
              .append(truncate(g.content.toString(), 3000)).append("\n\n");
        }
        sb.append("【学生问题】\n").append(question)
          .append("\n\n请严格依据上述资料回答。引用规则：每个来源编号全文只标注一次，格式为 [来源N]；"
                  + "多个要点来自同一来源时，只在该来源首次出现处标注，不要重复标注、不要在末尾罗列全部编号；"
                  + "资料未覆盖的内容请明确说明，不要编造。");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 兜底去重：模型仍可能重复标注同一来源，落库前只保留每个编号的首次出现。 */
    private static String dedupeCitations(String text) {
        if (text == null) return "";
        Set<String> seen = new HashSet<>();
        Matcher m = Pattern.compile("\\[来源(\\d+)\\]").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, seen.add(m.group(1)) ? Matcher.quoteReplacement(m.group()) : "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private boolean isGreeting(String q) {
        if (q == null) return false;
        String s = q.trim().toLowerCase();
        return GREETINGS.contains(s);
    }

    private void saveMessage(Long sessionId, Long userId, String question, String rewriteQuery, String answer, List<Map<String, Object>> sources) {
        if (userId == null) return; // 未登录：不落库
        // 更新会话标题 + 消息数
        if (sessionId != null) {
            try {
                ChatSession session = sessionMapper.selectById(sessionId);
                if (session != null) {
                    if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
                        String t = question == null ? "" : question.trim();
                        session.setTitle(t.length() > 20 ? t.substring(0, 20) : t);
                    }
                    session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
                    sessionMapper.updateById(session);
                }
            } catch (Exception e) {
                log.warn("更新会话标题失败: sessionId={}", sessionId, e);
            }
        }
        try {
            ChatMessage m = new ChatMessage();
            m.setSessionId(sessionId == null ? 0L : sessionId);
            m.setUserId(userId);
            m.setRole("user");
            m.setContent(question);
            m.setRetrieveQuery(rewriteQuery);
            messageMapper.insert(m);
            ChatMessage a = new ChatMessage();
            a.setSessionId(sessionId == null ? 0L : sessionId);
            a.setUserId(userId);
            a.setRole("assistant");
            a.setContent(answer);
            a.setSources(objectMapper.writeValueAsString(sources));
            a.setHitCount(sources.size());
            messageMapper.insert(a);
        } catch (Exception e) {
            log.warn("保存问答消息失败: sessionId={}, userId={}", sessionId, userId, e);
        }
    }

    private void emit(SseEmitter emitter, String type, Object data, AtomicBoolean alive) {
        if (!alive.get()) throw new IllegalStateException("客户端已断开");
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        if (data != null) m.put("data", data);
        try {
            // send 失败即视为客户端断开：置 alive=false 并抛出，中断后续生成与落库
            emitter.send(SseEmitter.event().data(m));
        } catch (IOException e) {
            alive.set(false);
            throw new IllegalStateException("客户端已断开", e);
        }
    }
}
