package com.campuspedia.controller;
/**
 * 控制层：管理员对用户账号的查询、修改与删除，挂载于 /api/admin/users。
 * <p>由 SecurityConfig 限定仅管理员可访问，删除时级联清理该用户的会话与消息。</p>
 */

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.ChatMessage;
import com.campuspedia.entity.ChatSession;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.ChatMessageMapper;
import com.campuspedia.mapper.ChatSessionMapper;
import com.campuspedia.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员用户接口：列出、修改与删除用户账号。
 * <p>列表统一剔除密码哈希；删除按事务级联清理该用户的消息与会话。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserMapper userMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 注入用户、会话与消息 Mapper，供账号维护与级联删除使用。
     *
     * @param userMapper         用户表读写
     * @param chatSessionMapper  删除用户时清理其会话
     * @param chatMessageMapper  删除用户时清理其消息
     */
    public UserController(UserMapper userMapper, ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper) {
        this.userMapper = userMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    /**
     * 按 id 升序列出全部用户，并在返回前逐个清空密码字段。
     * <p>剔除凭据摘要，避免批量下发密码哈希。</p>
     *
     * @return 不含密码的用户列表
     */
    @GetMapping
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().orderByAsc("id"));
        for (User u : users) u.setPassword(null);
        return Result.ok(users);
    }

    /**
     * 局部更新指定用户，仅覆盖请求中非 null 的字段（含角色）。
     * <p>副作用是更新一条用户记录；用户不存在时抛 BizException。</p>
     *
     * @param id   用户主键
     * @param body 含可更新字段的用户实体
     * @return 无 data 的成功响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody User body) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if (body.getRole() != null) u.setRole(body.getRole());
        if (body.getUsername() != null) u.setUsername(body.getUsername());
        if (body.getMajor() != null) u.setMajor(body.getMajor());
        if (body.getClassName() != null) u.setClassName(body.getClassName());
        userMapper.updateById(u);
        return Result.ok(null);
    }

    /**
     * 删除指定用户，并在事务内先清理其消息与会话再删用户，避免残留关联数据。
     * <p>副作用是多表删除；用户不存在时抛 BizException。</p>
     *
     * @param id 用户主键
     * @return 无 data 的成功响应
     */
    @Transactional
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        // 级联删除：聊天消息 + 会话 + 用户
        chatMessageMapper.delete(new QueryWrapper<ChatMessage>().eq("user_id", id));
        chatSessionMapper.delete(new QueryWrapper<ChatSession>().eq("user_id", id));
        userMapper.deleteById(id);
        return Result.ok(null);
    }
}
