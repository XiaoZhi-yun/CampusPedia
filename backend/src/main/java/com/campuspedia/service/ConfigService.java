package com.campuspedia.service;
/**
 * 运行时键值配置读取，隶属业务服务层（service 包），屏蔽 {@code t_sys_config} 表访问细节。
 *
 * <p>上游：{@code KnowledgeService}（切片参数）、{@code RagChain}（召回/重排/提示词开关）等按 key 读取；
 * 下游：{@code SysConfigMapper} 查询 MySQL 的 {@code t_sys_config} 键值表。
 * 契约：取值一律带默认值，配置缺失或解析失败时静默回退，不因配置问题中断问答主链路。</p>
 */

import com.campuspedia.entity.SysConfig;
import com.campuspedia.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

/**
 * 把 {@code t_sys_config} 的键值配置暴露为带默认值的类型化读取。
 *
 * <p>设计动机：召回条数、阈值、重排开关等需要运营可调，但不能让坏配置拖垮问答，
 * 因此读取失败一律返回默认值而非抛异常。</p>
 */
@Service
public class ConfigService {
    /** 以配置键为主键查询 {@code t_sys_config} 的 Mapper；key 即表主键，故用 selectById。 */
    private final SysConfigMapper mapper;
    /**
     * 构造配置读取服务。
     *
     * @param mapper {@code t_sys_config} 的 Mapper，由 Spring 注入
     */
    public ConfigService(SysConfigMapper mapper) { this.mapper = mapper; }
    /**
     * 读取字符串配置。
     *
     * @param key          配置键
     * @param defaultValue 配置行不存在或值为 {@code null} 时返回的兜底值
     * @return 配置值或 {@code defaultValue}；不抛异常
     */
    public String get(String key, String defaultValue) {
        SysConfig c = mapper.selectById(key);
        return (c == null || c.getConfigValue() == null) ? defaultValue : c.getConfigValue();
    }
    /**
     * 读取整型配置，解析失败时静默回退默认值。
     *
     * @param key 配置键
     * @param def 兜底值
     * @return 解析后的整数或 {@code def}；非法数值不抛异常
     */
    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
    }
    /**
     * 读取布尔配置。
     *
     * @param key 配置键
     * @param def 兜底值
     * @return {@code Boolean.parseBoolean} 的结果；配置缺失时等价于 {@code def}
     */
    public boolean getBool(String key, boolean def) { return Boolean.parseBoolean(get(key, String.valueOf(def)).trim()); }
}
