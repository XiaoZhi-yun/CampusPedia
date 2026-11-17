package com.campuspedia.security;
/**
 * 安全层：基于内存滑动窗口的限流过滤器，按 IP 约束问答与登录/注册频次。
 * <p>命中的请求直接返回 429 并短路，避免后续业务与下游 LLM/embedding 调用被刷。</p>
 */

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 简易限流：问答每 IP 每分钟 20 次，登录/注册每 IP 每分钟 10 次，防 API key 被刷与口令暴力尝试。 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** 按 IP+URI 分桶的请求时间戳队列，作为滑动窗口计数依据。 */
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private static final int CHAT_MAX = 20;
    private static final int AUTH_MAX = 10;
    /** 窗口 60s：既用于请求计数，也用于判定队列与分桶是否过期可清理。 */
    private static final long WINDOW = 60_000L;
    private volatile long lastCleanup = System.currentTimeMillis();

    /**
     * 对问答与登录/注册路径按 IP 做滑动窗口计数，超阈值直接返回 429 并短路过滤链。
     * <p>其余路径不计数直接放行；限流命中时不调用 chain.doFilter，避免进入下游业务。</p>
     *
     * @param request  用于取 URI 与客户端 IP 作为限流维度
     * @param response 超限时写入 429 与固定 JSON 提示
     * @param chain    未超限时继续传递的过滤链
     * @throws ServletException 交由下游过滤器抛出
     * @throws IOException      写响应或下游 IO 失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        int max = 0;
        if (uri.startsWith("/api/chat/stream")) max = CHAT_MAX;
        else if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")) max = AUTH_MAX;

        if (max > 0) {
            cleanupIfNeeded();
            String key = request.getRemoteAddr() + "|" + uri;
            long now = System.currentTimeMillis();
            Deque<Long> dq = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (dq) {
                while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW) dq.pollFirst();
                if (dq.size() >= max) {
                    /** 直接短路并回固定提示，不暴露剩余配额或内部计数。 */
                    response.setStatus(429);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
                    return;
                }
                dq.addLast(now);
            }
        }
        chain.doFilter(request, response);
    }

    /** 定期清理过期计数，避免 IP 累积导致内存无上限。 */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < WINDOW) return;
        lastCleanup = now;
        hits.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || now - d.peekLast() > WINDOW * 2;
            }
        });
    }
}
