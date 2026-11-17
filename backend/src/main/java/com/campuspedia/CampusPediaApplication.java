package com.campuspedia;

/**
 * 应用入口：Spring Boot 启动类。
 *
 * <p>扫描 com.campuspedia 下的 Controller / Service / Mapper / Security 过滤器，
 * 并加载 application.yml 的数据源、JWT、LLM 与 pgvector 配置。
 * JwtUtil 在启动阶段校验密钥，缺失即 fail-fast，避免带病运行。</p>
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启用自动配置与组件扫描；本包为根包，子包内的 REST、RAG 与安全组件都会纳入容器。
 */
@SpringBootApplication
public class CampusPediaApplication {

    /**
     * 启动内嵌 Web 容器并刷新应用上下文。
     *
     * @param args 命令行参数，可覆盖 Spring 配置（如 --server.port=8082）
     */
    public static void main(String[] args) {
        // 交给 Spring Boot 完成自动装配；端口默认 8081，避开本机 8080 上的其他服务
        SpringApplication.run(CampusPediaApplication.class, args);
    }
}
