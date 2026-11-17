package com.campuspedia.security;
/**
 * 安全层：解析 Bearer token 并构建 Spring Security 认证上下文。
 * <p>每请求实时查库确认用户状态与角色，由 SecurityConfig 置于认证过滤器之前。</p>
 */

import com.campuspedia.entity.User;
import com.campuspedia.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 解析 token 并实时查库：禁用用户拦截、角色变更即时生效。 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /**
     * 由 Spring 注入 JWT 工具与用户 Mapper。
     *
     * @param jwtUtil    用于解析校验 token
     * @param userMapper 用于实时查询用户状态与角色
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    /**
     * 解析 Bearer token，查库确认用户启用后写入 SecurityContext 认证信息。
     * <p>解析失败或用户被禁用则不设置认证，交由后续授权规则按未登录拒绝；过滤链始终继续传递。</p>
     *
     * @param request  取 Authorization 头
     * @param response 未直接使用
     * @param chain    始终继续传递的过滤链
     * @throws ServletException 下游过滤器抛出
     * @throws IOException      下游 IO 失败时抛出
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(auth.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                User user = userMapper.selectById(userId);
                /** 仅 status=1 的启用用户建立认证，被禁用账号即使持有有效 token 也视为未登录。 */
                if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                    String role = user.getRole();
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    var authentication = new UsernamePasswordAuthenticationToken(user.getStudentNo(), null, authorities);
                    authentication.setDetails(Map.of("userId", userId));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // 无效/过期 token 按未登录处理，保留 debug 日志便于排查
                log.debug("JWT 解析失败，按未登录处理", e);
            }
        }
        chain.doFilter(request, response);
    }
}
