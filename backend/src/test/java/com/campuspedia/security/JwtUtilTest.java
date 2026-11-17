package com.campuspedia.security;
/** 覆盖模块：JWT 签发与解析（JwtUtil）；回归目标：往返一致、篡改/过期失效、空密钥 fail-fast。 */

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：签发/解析往返、篡改失效、过期失效、空密钥启动即失败。
 */
class JwtUtilTest {

    private static final String SECRET = "campus-pedia-test-secret-key-0123456789";

    private JwtUtil newUtil(long hours) throws Exception {
        JwtUtil u = new JwtUtil();
        Field f = JwtUtil.class.getDeclaredField("secret");
        f.setAccessible(true);
        f.set(u, SECRET);
        Field h = JwtUtil.class.getDeclaredField("expireHours");
        h.setAccessible(true);
        h.setLong(u, hours);
        return u;
    }

    /**
     * 被测行为：签发 token 后能解析回原始用户声明。
     * 前置：反射注入合法密钥与 24 小时有效期，签发 uid=123、用户名、角色。
     * 期望：subject/username/role 三项与签发入参一致。
     * 回归：claim 键名或类型写错导致解析后取不到用户身份的缺陷。
     */
    @Test
    void generateAndParseRoundTrip() throws Exception {
        JwtUtil u = newUtil(24);
        String token = u.generate(123L, "张三", "user");
        var claims = u.parse(token);
        assertEquals("123", claims.getSubject());
        assertEquals("张三", claims.get("username"));
        assertEquals("user", claims.get("role"));
    }

    /**
     * 被测行为：签名段被篡改的 token 解析失败。
     * 前置：合法密钥签发后替换末尾 3 个字符伪造签名。
     * 期望：parse 抛异常，不接受伪造 token。
     * 回归：签名校验被绕过导致任意伪造 token 可通过鉴权的缺陷。
     */
    @Test
    void tamperedTokenFails() throws Exception {
        JwtUtil u = newUtil(24);
        String token = u.generate(1L, "张三", "user");
        String tampered = token.substring(0, token.length() - 3) + "xxx";
        assertThrows(Exception.class, () -> u.parse(tampered));
    }

    /**
     * 被测行为：已过期 token 解析失败。
     * 前置：反射把 expireHours 置为 -1，使签发瞬间即过期。
     * 期望：parse 抛出过期相关异常。
     * 回归：过期时间未校验导致历史 token 长期有效的缺陷。
     */
    @Test
    void expiredTokenFails() throws Exception {
        JwtUtil u = newUtil(-1);
        String token = u.generate(1L, "张三", "user");
        assertThrows(Exception.class, () -> u.parse(token));
    }

    // 前置：不注入任何字段直接调用 validateSecret；期望：IllegalStateException，而非登录时才报弱密钥。
    /** 回归：未配置 JWT_SECRET 时启动即报错，而不是登录时才抛 WeakKeyException。 */
    @Test
    void blankSecretFailsFast() {
        JwtUtil u = new JwtUtil();
        // 断言落在 validateSecret 而非 generate 上，确认 fail-fast 发生在启动校验，避免缺陷被推迟到运行期。
        assertThrows(IllegalStateException.class, u::validateSecret);
    }
}
