package com.campuspedia.controller;
/**
 * 控制层：校园 RAG 问答的 SSE 流式入口 /api/chat/stream。
 * <p>建立流之前校验会话归属，随后把问题交给 RagChain 异步生成并记录问答日志。</p>
 */

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

/**
 * 问答流式接口：建立 SSE 连接，把问题与历史交给 RagChain 逐段回推。
 * <p>连接建立前校验前端传入 sessionId 的归属，并记录问答日志。</p>
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final RagChain ragChain;
    private final AuthHelper authHelper;
    private final SysLogMapper sysLogMapper;
    private final ChatSessionMapper sessionMapper;

    /**
     * 注入 RAG 链、鉴权辅助与日志/会话 Mapper。
     *
     * @param ragChain      执行检索增强生成与流式输出
     * @param authHelper    解析当前登录用户 id
     * @param sysLogMapper  记录问答日志
     * @param sessionMapper 校验会话归属
     */
    public ChatController(RagChain ragChain, AuthHelper authHelper, SysLogMapper sysLogMapper, ChatSessionMapper sessionMapper) {
        this.ragChain = ragChain;
        this.authHelper = authHelper;
        this.sysLogMapper = sysLogMapper;
        this.sessionMapper = sessionMapper;
    }

    /**
     * 建立 SSE 流：校验会话归属、记录日志后异步触发 RAG 流式生成。
     * <p>返回后由后台线程持续向 emitter 推送；sessionId 不属于当前用户时抛 403。</p>
     *
     * @param body    含 question/sessionId/history 的请求体
     * @param request 用于解析登录用户与来源 IP
     * @return 120 秒超时的 SseEmitter，流式结果由后台推送
     */
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
            /** 日志写入失败不中断问答主流程，降级记录后继续建立流。 */
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
