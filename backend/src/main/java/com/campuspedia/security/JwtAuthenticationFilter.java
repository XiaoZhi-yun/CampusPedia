package com.campuspedia.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 解析 Authorization: Bearer token，注入 SecurityContext（无 token 或无效则匿名）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(auth.substring(7));
                String role = claims.get("role", String.class);
                String username = claims.get("username", String.class);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                var authentication = new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(Map.of("userId", Long.valueOf(claims.getSubject())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignore) {
                // token 无效 → 匿名
            }
        }
        chain.doFilter(request, response);
    }
}
