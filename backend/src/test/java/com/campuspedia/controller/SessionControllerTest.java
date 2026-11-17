package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 会话接口回归：读取、改名、删除都必须校验会话归属（IDOR 防护）。
 * 纯 Mockito 单元测试，不启动 Spring 上下文、不依赖数据库。
 */
class SessionControllerTest {

    private ChatSessionMapper sessionMapper;
    private ChatMessageMapper messageMapper;
    private HttpServletRequest request;
    private SessionController controller;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ChatSessionMapper.class);
        messageMapper = mock(ChatMessageMapper.class);
        AuthHelper authHelper = mock(AuthHelper.class);
        request = mock(HttpServletRequest.class);
        controller = new SessionController(sessionMapper, messageMapper, authHelper);
        when(authHelper.currentUserId(request)).thenReturn(1L);
    }

    private ChatSession sessionOf(Long ownerId) {
        ChatSession s = new ChatSession();
        s.setId(7L);
        s.setUserId(ownerId);
        s.setTitle("新对话");
        s.setMessageCount(0);
        return s;
    }

    @Test
    void otherUserCannotReadMessages() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class, () -> controller.messages(7L, request));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void otherUserCannotRename() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class,
                () -> controller.rename(7L, Map.of("title", "hacked"), request));

        assertEquals(403, ex.getCode());
        verify(sessionMapper).selectById(7L);
        verifyNoMoreInteractions(sessionMapper);
    }

    @Test
    void otherUserCannotDelete() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class, () -> controller.delete(7L, request));

        assertEquals(403, ex.getCode());
        verify(sessionMapper).selectById(7L);
        verifyNoMoreInteractions(sessionMapper);
        verifyNoInteractions(messageMapper);
    }

    @Test
    void missingSessionIsRejected() {
        when(sessionMapper.selectById(7L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> controller.messages(7L, request));

        assertEquals(400, ex.getCode());
        assertEquals("会话不存在", ex.getMessage());
        verifyNoInteractions(messageMapper);
    }

    @Test
    void ownerCanReadRenameAndDelete() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(1L));

        assertEquals(200, controller.messages(7L, request).code());
        assertEquals(200, controller.rename(7L, Map.of("title", "改名"), request).code());
        assertEquals(200, controller.delete(7L, request).code());

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals("改名", captor.getValue().getTitle());
        verify(sessionMapper).deleteById(7L);
        verify(messageMapper).selectList(any());
    }

    @Test
    void anonymousRequestIsRejected() {
        // 另建一个未登录的 controller，覆盖 uid() 的空用户分支
        AuthHelper anonymous = mock(AuthHelper.class);
        when(anonymous.currentUserId(request)).thenReturn(null); // Mockito 对 Long 默认返回 0L，需显式置空
        SessionController anonymousController =
                new SessionController(sessionMapper, messageMapper, anonymous);

        BizException ex = assertThrows(BizException.class, () -> anonymousController.list(request));
        assertEquals(401, ex.getCode());
        verifyNoInteractions(sessionMapper, messageMapper);
    }
}
