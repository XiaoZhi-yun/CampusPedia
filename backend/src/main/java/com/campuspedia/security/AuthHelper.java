package com.campuspedia.security;

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

    /** 当前登录用户 id；未登录或 token 无效返回 null（游客）。 */
    public Long currentUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
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
