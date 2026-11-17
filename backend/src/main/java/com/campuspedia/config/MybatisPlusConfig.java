package com.campuspedia.config;
/**
 * 配置层：注册 MyBatis-Plus 分页拦截器，供各 Mapper 的分页查询复用。
 * <p>统一采用 MySQL 方言，使 selectPage 等分页 API 全局生效。</p>
 */

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 装配分页插件：注入 MySQL 方言的分页内部拦截器。
     *
     * @return 携带分页拦截器的实例，供所有 Mapper 的分页查询复用
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
