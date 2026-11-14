package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import com.campuspedia.security.AuthHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserMapper userMapper;
    private final AuthHelper authHelper;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserMapper userMapper, AuthHelper authHelper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.authHelper = authHelper;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public Result<User> get(HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        u.setPassword(null);
        return Result.ok(u);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        if (!passwordEncoder.matches(body.get("oldPassword"), u.getPassword())) throw new BizException("原密码错误");
        String np = body.get("newPassword");
        if (np == null || np.length() < 6 || np.length() > 20) throw new BizException("新密码长度需 6-20 位");
        u.setPassword(passwordEncoder.encode(np));
        userMapper.updateById(u);
        return Result.ok(null);
    }

    @PutMapping
    public Result<User> update(@RequestBody User body, HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        if (body.getUsername() != null) u.setUsername(body.getUsername());
        if (body.getMajor() != null) u.setMajor(body.getMajor());
        if (body.getClassName() != null) u.setClassName(body.getClassName());
        if (body.getAvatar() != null) u.setAvatar(body.getAvatar());
        userMapper.updateById(u);
        u.setPassword(null);
        return Result.ok(u);
    }
}
