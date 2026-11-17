package com.campuspedia.security;

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

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private static final int CHAT_MAX = 20;
    private static final int AUTH_MAX = 10;
    private static final long WINDOW = 60_000L;
    private volatile long lastCleanup = System.currentTimeMillis();

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
