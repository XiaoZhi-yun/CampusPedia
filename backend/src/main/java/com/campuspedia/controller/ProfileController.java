package com.campuspedia.controller;
/**
 * 控制层：当前登录用户的资料读取、修改与改密，挂载于 /api/profile。
 * <p>身份一律从 JWT 解析而非取请求参数，避免越权访问他人资料。</p>
 */

import com.campuspedia.common.BizException;
import com.campuspedia.common.Result;
import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import com.campuspedia.security.AuthHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 个人资料接口：读取/修改当前用户资料与修改密码。
 * <p>身份取自 JWT 而非请求参数，返回前统一剔除密码哈希。</p>
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserMapper userMapper;
    private final AuthHelper authHelper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 注入用户 Mapper、鉴权辅助与口令编码器。
     *
     * @param userMapper      用户资料读写
     * @param authHelper      解析当前登录用户 id
     * @param passwordEncoder 校验原密码并加密新密码
     */
    public ProfileController(UserMapper userMapper, AuthHelper authHelper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.authHelper = authHelper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 返回当前登录用户资料，并在响应前清空密码字段。
     * <p>未登录时抛 401；返回实体不含凭据摘要。</p>
     *
     * @param request 用于从 JWT 解析当前用户 id
     * @return 已剔除密码的用户资料
     */
    @GetMapping
    public Result<User> get(HttpServletRequest request) {
        Long userId = authHelper.currentUserId(request);
        if (userId == null) throw new BizException(401, "未登录");
        User u = userMapper.selectById(userId);
        /** 返回前清空密码哈希，避免把凭据摘要下发到前端。 */
        u.setPassword(null);
        return Result.ok(u);
    }

    /**
     * 修改当前用户密码：先校验原密码，再校验新密码长度后加密写回。
     * <p>副作用是更新一条用户记录；原密码错误或长度不合规抛 BizException。</p>
     *
     * @param body    含 oldPassword/newPassword 的请求体
     * @param request 用于解析当前用户 id
     * @return 无 data 的成功响应
     */
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

    /**
     * 局部更新当前用户资料，仅覆盖请求中非 null 的字段，并剔除返回体中的密码。
     * <p>副作用是更新一条用户记录；未登录时抛 401。</p>
     *
     * @param body    含可更新字段的用户实体
     * @param request 用于解析当前用户 id
     * @return 已剔除密码的用户资料
     */
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
