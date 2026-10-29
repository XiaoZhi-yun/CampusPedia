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

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) throw new BizException("用户名不能为空");
        if (username.length() < 3 || username.length() > 20) throw new BizException("用户名长度需 3-20 位");
        if (username.matches(".*\\s.*")) throw new BizException("用户名不能包含空格");
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) throw new BizException("密码不能为空");
        if (password.length() < 6 || password.length() > 20) throw new BizException("密码长度需 6-20 位");
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        validateUsername(username);
        validatePassword(password);
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
        validateUsername(body.get("username"));
        validatePassword(body.get("password"));
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
