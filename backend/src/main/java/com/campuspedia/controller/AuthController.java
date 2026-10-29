package com.campuspedia.controller;

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import com.campuspedia.security.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException("用户名和密码不能为空");
        }
        Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        if (exists != null && exists > 0) throw new BizException("用户名已存在");
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole("user");
        u.setStatus(1);
        userMapper.insert(u);
        return Result.ok(loginResult(u), "注册成功");
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("username", body.get("username")));
        if (u == null || !passwordEncoder.matches(body.get("password"), u.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        return Result.ok(loginResult(u), "登录成功");
    }

    private Map<String, Object> loginResult(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwtUtil.generate(u.getId(), u.getUsername(), u.getRole()));
        m.put("userId", u.getId());
        m.put("username", u.getUsername());
        m.put("role", u.getRole());
        return m;
    }
}
