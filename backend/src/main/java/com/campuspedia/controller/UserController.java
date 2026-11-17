package com.campuspedia.controller;

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

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserMapper userMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    public UserController(UserMapper userMapper, ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper) {
        this.userMapper = userMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    @GetMapping
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().orderByAsc("id"));
        for (User u : users) u.setPassword(null);
        return Result.ok(users);
    }

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
