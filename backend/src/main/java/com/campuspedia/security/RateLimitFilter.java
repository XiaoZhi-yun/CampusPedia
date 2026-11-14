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

/** 简易限流：/api/chat/stream 每 IP 每分钟最多 20 次，防 API key 被刷。 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private static final int MAX = 20;
    private static final long WINDOW = 60_000L;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/chat/stream")) {
            String ip = request.getRemoteAddr();
            long now = System.currentTimeMillis();
            Deque<Long> dq = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
            synchronized (dq) {
                while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW) dq.pollFirst();
                if (dq.size() >= MAX) {
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
}
