package com.campuspedia.security;

import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationFilter 单测：有效 token、禁用用户、无 token 三种场景。
 */
class JwtAuthenticationFilterTest {

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private JwtAuthenticationFilter newFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        return new JwtAuthenticationFilter(jwtUtil, userMapper);
    }

    @Test
    void validTokenSetsAuthentication() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(jwtUtil.parse("token")).thenReturn(claims);

        User user = new User();
        user.setId(1L);
        user.setStudentNo("2021001");
        user.setRole("user");
        user.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(user);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter(jwtUtil, userMapper).doFilterInternal(req, resp, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, resp);
    }

    @Test
    void disabledUserNotAuthenticated() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1");
        when(jwtUtil.parse("token")).thenReturn(claims);

        User user = new User();
        user.setId(1L);
        user.setRole("user");
        user.setStatus(0);
        when(userMapper.selectById(1L)).thenReturn(user);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer token");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter(jwtUtil, userMapper).doFilterInternal(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void noHeaderSkipsAuthentication() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        UserMapper userMapper = mock(UserMapper.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        newFilter(jwtUtil, userMapper).doFilterInternal(req, resp, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, resp);
    }
}
