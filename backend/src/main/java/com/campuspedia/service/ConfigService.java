package com.campuspedia.service;

import com.campuspedia.entity.SysConfig;
import com.campuspedia.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

@Service
public class ConfigService {
    private final SysConfigMapper mapper;
    public ConfigService(SysConfigMapper mapper) { this.mapper = mapper; }
    public String get(String key, String defaultValue) {
        SysConfig c = mapper.selectById(key);
        return (c == null || c.getConfigValue() == null) ? defaultValue : c.getConfigValue();
    }
    public int getInt(String key, int def) {
        try { return Integer.parseInt(get(key, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
    }
    public boolean getBool(String key, boolean def) { return Boolean.parseBoolean(get(key, String.valueOf(def)).trim()); }
}
