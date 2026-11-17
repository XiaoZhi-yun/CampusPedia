package com.campuspedia.security;
/**
 * 安全层：Controller 侧鉴权入口，从 Authorization 头解析 JWT 并判定登录态与管理员角色。
 * <p>依赖 JwtUtil 校验签名，供需要按当前用户隔离数据的接口取用身份。</p>
 */

import com.campuspedia.common.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 鉴权辅助：从请求头解析 JWT，校验登录态与管理员角色。
 */
@Component
public class AuthHelper {

    private final JwtUtil jwtUtil;

    /**
     * 注入 JWT 工具，由 Spring 构造时传入。
     *
     * @param jwtUtil 用于解析校验 Authorization 头中的 token
     */
    public AuthHelper(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /** 解析当前登录用户（Claims 内含 userId/username/role）。 */
    public Claims requireUser(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BizException(401, "未登录");
        }
        return jwtUtil.parse(auth.substring(7));
    }

    /** 当前登录用户 id；未登录或 token 无效返回 null。 */
    public Long currentUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
            /** subject 即签发时写入的用户 id，是下游按用户隔离数据的身份依据。 */
            return Long.valueOf(jwtUtil.parse(auth.substring(7)).getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验管理员角色。 */
    public Claims requireAdmin(HttpServletRequest request) {
        Claims claims = requireUser(request);
        if (!"admin".equals(claims.get("role"))) {
            throw new BizException(403, "无权限");
        }
        return claims;
    }
}
