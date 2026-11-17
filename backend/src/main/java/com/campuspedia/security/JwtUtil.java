package com.campuspedia.security;
/**
 * 安全层：JWT 签发与校验（HS256），密钥取自 campus.jwt.secret 配置。
 * <p>AuthController 登录成功后签发，JwtAuthenticationFilter 与 AuthHelper 负责解析校验。</p>
 */

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析（HS256）。
 */
@Component
public class JwtUtil {

    @Value("${campus.jwt.secret}")
    /** HS256 密钥；启动时由 validateSecret 校验，空值或不足 32 字节直接让应用启动失败。 */
    private String secret;

    @Value("${campus.jwt.expire-hours:24}")
    /** token 有效期（小时），签发时换算为 expiration 时间。 */
    private long expireHours;

    /** 启动时校验密钥，避免空/过短密钥在登录时才抛 WeakKeyException。 */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "campus.jwt.secret 未配置或不足 32 字节：请在 .env 或 application-local.yml 中设置 JWT_SECRET");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 JWT：以用户 id 作为 subject，附带用户名与角色声明。
     * <p>登录成功后调用；subject 是 AuthHelper 与 JwtAuthenticationFilter 还原身份的依据。</p>
     *
     * @param userId   用户主键，写入 subject
     * @param username 用户名声明
     * @param role     角色声明，供鉴权与 hasRole 判定
     * @return HS256 签名后的 token 字符串
     */
    public String generate(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expireHours * 3600_000L))
                .signWith(key())
                .compact();
    }

    /**
     * 校验签名并解析 token 返回声明；签名无效或已过期时抛 JJWT 异常，由调用方按未登录处理。
     *
     * @param token 待校验的 JWT 字符串
     * @return 含 subject 与自定义声明的 Claims
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }
}