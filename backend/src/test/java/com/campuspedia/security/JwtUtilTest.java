package com.campuspedia.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：签发/解析往返、篡改失效、过期失效。
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

    @Test
    void generateAndParseRoundTrip() throws Exception {
        JwtUtil u = newUtil(24);
        String token = u.generate(123L, "张三", "user");
        var claims = u.parse(token);
        assertEquals("123", claims.getSubject());
        assertEquals("张三", claims.get("username"));
        assertEquals("user", claims.get("role"));
    }

    @Test
    void tamperedTokenFails() throws Exception {
        JwtUtil u = newUtil(24);
        String token = u.generate(1L, "张三", "user");
        String tampered = token.substring(0, token.length() - 3) + "xxx";
        assertThrows(Exception.class, () -> u.parse(tampered));
    }

    @Test
    void expiredTokenFails() throws Exception {
        JwtUtil u = newUtil(-1);
        String token = u.generate(1L, "张三", "user");
        assertThrows(Exception.class, () -> u.parse(token));
    }
}
