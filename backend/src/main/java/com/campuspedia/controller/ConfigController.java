package com.campuspedia.controller;
/**
 * 控制层：系统参数配置的读取与批量更新，挂载于 /api/config。
 * <p>由 SecurityConfig 限定仅管理员可访问，下游直接读写 SysConfigMapper。</p>
 */

import com.campuspedia.common.Result;
import com.campuspedia.entity.SysConfig;
import com.campuspedia.mapper.SysConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置接口：读取全部配置项并按提交的列表逐条更新。
 * <p>直接读写 SysConfigMapper，路径仅管理员可访问。</p>
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final SysConfigMapper mapper;

    /**
     * 注入系统配置 Mapper。
     *
     * @param mapper 配置表读写
     */
    public ConfigController(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 返回全部系统配置项，供管理端展示与编辑。
     *
     * @return 配置项列表
     */
    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.ok(mapper.selectList(null));
    }

    /**
     * 按提交列表逐条更新配置项，副作用是修改多条配置记录。
     *
     * @param configs 待更新的配置项列表，按 id 覆盖
     * @return 无 data 的成功响应
     */
    @PutMapping
    public Result<Void> update(@RequestBody List<SysConfig> configs) {
        for (SysConfig c : configs) {
            mapper.updateById(c);
        }
        return Result.ok(null);
    }
}
