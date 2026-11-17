package com.campuspedia.controller;
/** 覆盖模块：会话接口（读/改名/删除/列表）；回归目标：会话归属越权、会话不存在、owner 正常、匿名 401。 */

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

    /**
     * 被测行为：非会话拥有者读取消息被拒绝。
     * 前置：当前用户 id=1，会话 7 属于用户 2。
     * 期望：抛出 403 BizException，且消息 mapper 完全不被调用。
     * 回归：仅凭会话 id 读取他人消息的水平越权（IDOR）缺陷。
     */
    @Test
    void otherUserCannotReadMessages() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class, () -> controller.messages(7L, request));

        assertEquals(403, ex.getCode());
        verifyNoInteractions(messageMapper);
    }

    /**
     * 被测行为：非会话拥有者改名被拒绝。
     * 前置：会话 7 属于用户 2，传入新标题 hacked。
     * 期望：抛出 403，且除按 id 查询会话外不再执行任何写操作。
     * 回归：改名接口漏做归属校验、被他人篡改会话标题的缺陷。
     */
    @Test
    void otherUserCannotRename() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class,
                () -> controller.rename(7L, Map.of("title", "hacked"), request));

        assertEquals(403, ex.getCode());
        verify(sessionMapper).selectById(7L);
        verifyNoMoreInteractions(sessionMapper);
    }

    /**
     * 被测行为：非会话拥有者删除会话被拒绝。
     * 前置：会话 7 属于用户 2。
     * 期望：抛出 403，不触发 deleteById，也不触碰消息 mapper。
     * 回归：删除接口漏做归属校验、被他人删除会话及关联数据的缺陷。
     */
    @Test
    void otherUserCannotDelete() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(2L));

        BizException ex = assertThrows(BizException.class, () -> controller.delete(7L, request));

        assertEquals(403, ex.getCode());
        verify(sessionMapper).selectById(7L);
        verifyNoMoreInteractions(sessionMapper);
        verifyNoInteractions(messageMapper);
    }

    /**
     * 被测行为：会话不存在时返回明确的业务错误。
     * 前置：selectById 返回 null。
     * 期望：抛出 400 会话不存在，且不查消息。
     * 回归：空会话未判空导致 NPE 或把 404 误报成 500 的缺陷。
     */
    @Test
    void missingSessionIsRejected() {
        when(sessionMapper.selectById(7L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> controller.messages(7L, request));

        assertEquals(400, ex.getCode());
        assertEquals("会话不存在", ex.getMessage());
        verifyNoInteractions(messageMapper);
    }

    /**
     * 被测行为：拥有者对本会话的读、改名、删除均成功。
     * 前置：会话 7 属于当前用户 1。
     * 期望：三个接口都返回 200，改名写入 title，删除按 id 执行，消息按条件查询。
     * 回归：归属校验过严误伤拥有者、或改名/删除未真正落库的缺陷。
     */
    @Test
    void ownerCanReadRenameAndDelete() {
        when(sessionMapper.selectById(7L)).thenReturn(sessionOf(1L));

        assertEquals(200, controller.messages(7L, request).code());
        assertEquals(200, controller.rename(7L, Map.of("title", "改名"), request).code());
        assertEquals(200, controller.delete(7L, request).code());

        // updateById 以实体对象入参且存在重载，直接 verify 取不回写入值，需 ArgumentCaptor 捕获实参校验 title 真的被改。
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertEquals("改名", captor.getValue().getTitle());
        verify(sessionMapper).deleteById(7L);
        verify(messageMapper).selectList(any());
    }

    /**
     * 被测行为：未登录用户列出会话被拒绝。
     * 前置：另建 AuthHelper，currentUserId 显式返回 null。
     * 期望：抛出 401，且 session/message mapper 均不被调用。
     * 回归：匿名用户未拦截、直接以空 uid 查询全表会话的越权缺陷。
     */
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
