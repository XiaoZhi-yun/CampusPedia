package com.campuspedia.security;

import com.campuspedia.common.BizException;
import com.campuspedia.controller.SessionController;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
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
 * 安全相关单测：JwtAuthenticationFilter 三种场景，以及会话归属（越权）校验回归。
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

    /** 回归：登录用户不能读取他人会话消息，避免 IDOR。 */
    @Test
    void sessionOwnershipRejectsOtherUser() {
        ChatSessionMapper sessionMapper = mock(ChatSessionMapper.class);
        ChatMessageMapper messageMapper = mock(ChatMessageMapper.class);
        AuthHelper authHelper = mock(AuthHelper.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(authHelper.currentUserId(req)).thenReturn(1L);

        ChatSession other = new ChatSession();
        other.setId(9L);
        other.setUserId(2L);
        when(sessionMapper.selectById(9L)).thenReturn(other);

        SessionController controller = new SessionController(sessionMapper, messageMapper, authHelper);
        BizException ex = assertThrows(BizException.class, () -> controller.messages(9L, req));
        assertEquals(403, ex.getCode());
        verifyNoInteractions(messageMapper);
    }
}
