package com.campuspedia.security;
/** 覆盖模块：JWT 认证过滤器与会话归属校验；回归目标：有效 token 建认证、禁用用户/无 token 放行、会话越权拦截。 */

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

    /**
     * 被测行为：携带有效 token 且用户正常时建立认证并放行过滤器链。
     * 前置：mock JwtUtil 解析出 subject=1，mock UserMapper 返回启用用户，请求头带 Bearer token。
     * 期望：SecurityContext 出现认证主体，且 chain.doFilter 被调用。
     * 回归：token 合法却未写入 SecurityContext、后续接口仍视为未登录的缺陷。
     */
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

        // 认证主体必须写入 SecurityContext：这是过滤器与后续授权的唯一传递渠道，缺失即等同未登录。
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(req, resp);
    }

    /**
     * 被测行为：用户被禁用时即便 token 有效也不建立认证。
     * 前置：同上，但 mock 用户 status=0。
     * 期望：SecurityContext 中无认证主体。
     * 回归：封禁账号凭旧 token 仍可继续访问的越权缺陷。
     */
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

    /**
     * 被测行为：无 Authorization 头时跳过认证但继续放行请求。
     * 前置：getHeader 返回 null。
     * 期望：SecurityContext 无认证，chain.doFilter 仍被调用，匿名访问交给后续授权处理。
     * 回归：缺少请求头时过滤器异常中断、连公开接口都无法访问的缺陷。
     */
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

    // 前置：当前用户 id=1、会话 9 属于用户 2；期望：403 且消息 mapper 完全不被调用。
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
        // 越权必须在查询消息之前拦截：若 messageMapper 被触碰，说明校验发生在读数据之后，仍有泄露风险。
        verifyNoInteractions(messageMapper);
    }
}
