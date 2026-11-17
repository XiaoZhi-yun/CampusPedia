package com.campuspedia.config;
/**
 * 配置层：Spring Security 主配置，定义无状态 JWT 过滤链与 URL 授权规则。
 * <p>装配 JwtAuthenticationFilter 并收紧默认鉴权策略，是全部接口的授权入口。</p>
 */

import com.campuspedia.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：禁用 CSRF 与表单/基本认证，改为无状态 JWT 过滤链并按路径声明授权规则。
 * <p>公开、管理员与登录态接口逐一列出，其余请求统一要求已认证。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 注入自定义 JWT 过滤器，由 Spring 构造时传入。
     *
     * @param jwtAuthenticationFilter 置于认证过滤器之前解析 token 的过滤器
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 提供 BCrypt 密码编码器，供注册加密与登录校验共用。
     *
     * @return BCrypt 实现的 PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 构建过滤链：关闭 CSRF 与表单/基本认证，注册 JWT 过滤器，并声明各路径授权规则。
     * <p>副作用是把该链注册为容器级安全过滤器；规则按顺序匹配，末尾 anyRequest 兜底要求认证。</p>
     *
     * @param http Spring Security 提供的配置器
     * @return 构建完成的 SecurityFilterChain
     * @throws Exception 配置或构建过滤链失败时抛出
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .securityContext(c -> c.requireExplicitSave(false))
                .authorizeHttpRequests(auth -> auth
                        /** 登录/注册需公开以换取 token，健康检查供探活使用。 */
                        .requestMatchers("/api/auth/**", "/api/health").permitAll()
                        .requestMatchers("/api/chat/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/notice/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/notice/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/notice/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/notice/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/stats/topQuestions").authenticated()
                        .requestMatchers("/api/config/**", "/api/kb/**", "/api/stats/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/sessions/**", "/api/profile/**").authenticated()
                        /** 容器错误转发入口需放行，否则异常会被二次鉴权拦截、看不到真实错误。 */
                        .requestMatchers("/error").permitAll()
                        /** 兜底默认拒绝：新增接口若忘记显式放行会自动受保护，避免出现匿名入口。 */
                        .anyRequest().authenticated());
        return http.build();
    }
}
