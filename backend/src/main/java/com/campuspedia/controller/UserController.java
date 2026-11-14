package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) { this.userMapper = userMapper; }

    @GetMapping
    public Result<List<User>> list() {
        List<User> users = userMapper.selectList(new QueryWrapper<User>().orderByDesc("id"));
        for (User u : users) u.setPassword(null);
        return Result.ok(users);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody User body) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException("用户不存在");
        if (body.getRole() != null) u.setRole(body.getRole());
        if (body.getStatus() != null) u.setStatus(body.getStatus());
        userMapper.updateById(u);
        return Result.ok(null);
    }
}
