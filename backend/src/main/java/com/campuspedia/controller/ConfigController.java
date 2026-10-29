package com.campuspedia.controller;

import com.campuspedia.common.Result;
import com.campuspedia.entity.SysConfig;
import com.campuspedia.mapper.SysConfigMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final SysConfigMapper mapper;

    public ConfigController(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.ok(mapper.selectList(null));
    }

    @PutMapping
    public Result<Void> update(@RequestBody List<SysConfig> configs) {
        for (SysConfig c : configs) {
            mapper.updateById(c);
        }
        return Result.ok(null);
    }
}
