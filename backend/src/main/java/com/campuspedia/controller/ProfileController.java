package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import com.campuspedia.security.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserMapper userMapper;
    private final AuthHelper authHelper;

    public ProfileController(UserMapper userMapper, AuthHelper authHelper) {
        this.userMapper = userMapper;
        this.authHelper = authHelper;
    }

    @GetMapping
    public Result<User> get(HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        u.setPassword(null);
        return Result.ok(u);
    }

    @PutMapping
    public Result<User> update(@RequestBody User body, HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        if (body.getRealName() != null) u.setRealName(body.getRealName());
        if (body.getMajor() != null) u.setMajor(body.getMajor());
        if (body.getClassName() != null) u.setClassName(body.getClassName());
        if (body.getAvatar() != null) u.setAvatar(body.getAvatar());
        userMapper.updateById(u);
        u.setPassword(null);
        return Result.ok(u);
    }
}
