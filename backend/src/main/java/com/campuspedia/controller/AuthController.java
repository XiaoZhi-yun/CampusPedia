package com.campuspedia.controller;
/**
 * 控制层：注册与登录入口，属于 /api/auth 下无需认证的公开接口。
 * <p>校验输入后经 UserMapper 落库/查询，登录成功由 JwtUtil 签发 token 返回。</p>
 */

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

/**
 * 账号入口：校验注册信息、比对登录口令，成功后签发 JWT 并返回用户资料。
 * <p>注册与登录共用同一套输入校验与登录态结果封装，用户数据经 UserMapper 存取。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 注入用户 Mapper、口令编码器与 JWT 工具。
     *
     * @param userMapper      用户表读写
     * @param passwordEncoder 注册加密与登录校验共用
     * @param jwtUtil         登录成功后签发 token
     */
    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    private void validateUsername(String name) {
        if (name == null || name.isBlank()) throw new BizException("姓名不能为空");
        if (name.length() < 2 || name.length() > 20) throw new BizException("姓名长度需 2-20 位");
        if (name.matches(".*\\s.*")) throw new BizException("姓名不能包含空格");
    }

    private void validateStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) throw new BizException("学号不能为空");
        if (studentNo.length() < 4 || studentNo.length() > 20) throw new BizException("学号长度需 4-20 位");
        if (!studentNo.matches("[a-zA-Z0-9]+")) throw new BizException("学号只能包含字母和数字");
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) throw new BizException("密码不能为空");
        if (password.length() < 6 || password.length() > 20) throw new BizException("密码长度需 6-20 位");
    }

    /**
     * 注册新账号：校验学号/姓名/口令，确认学号未占用后写入默认角色与头像。
     * <p>副作用是插入一条用户记录；校验失败抛 BizException，由全局处理器转为 400。</p>
     *
     * @param body 含 studentNo/username/major/className/password 的请求体
     * @return 含 token 与用户资料的登录态结果，提示「注册成功」
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String studentNo = body.get("studentNo");
        String username = body.get("username");
        String major = body.get("major");
        String className = body.get("className");
        String password = body.get("password");
        validateStudentNo(studentNo);
        validateUsername(username);
        validatePassword(password);
        Long exists = userMapper.selectCount(new QueryWrapper<User>().eq("student_no", studentNo));
        /** 学号是登录唯一标识，写入前必须查重，避免同号多账号。 */
        if (exists != null && exists > 0) throw new BizException("该学号已被注册");
        User u = new User();
        u.setStudentNo(studentNo);
        u.setUsername(username);
        u.setMajor(major == null ? "" : major);
        u.setClassName(className == null ? "" : className);
        u.setPassword(passwordEncoder.encode(password));
        u.setAvatar("p1");
        u.setRole("user");
        u.setStatus(1);
        userMapper.insert(u);
        return Result.ok(loginResult(u), "注册成功");
    }

    /**
     * 登录：校验学号与口令，并拒绝被禁用账号，成功后签发 token。
     * <p>副作用是读取用户记录；失败分别以 401/403 表达凭据错误或账号禁用。</p>
     *
     * @param body 含 studentNo/password 的请求体
     * @return 含 token 与用户资料的登录态结果，提示「登录成功」
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String studentNo = body.get("studentNo");
        String password = body.get("password");
        validateStudentNo(studentNo);
        validatePassword(password);
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("student_no", studentNo));
        if (u == null || !passwordEncoder.matches(password, u.getPassword())) {
            /** 不区分「学号不存在」与「口令错误」，防止通过报错枚举已注册账号。 */
            throw new BizException(401, "学号或密码错误");
        }
        /** 已禁用账号即使口令正确也不得签发 token。 */
        if (u.getStatus() != null && u.getStatus() == 0) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }
        return Result.ok(loginResult(u), "登录成功");
    }

    private Map<String, Object> loginResult(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwtUtil.generate(u.getId(), u.getStudentNo(), u.getRole()));
        m.put("userId", u.getId());
        m.put("studentNo", u.getStudentNo());
        m.put("username", u.getUsername());
        m.put("major", u.getMajor());
        m.put("className", u.getClassName());
        m.put("avatar", u.getAvatar());
        m.put("role", u.getRole());
        return m;
    }
}
