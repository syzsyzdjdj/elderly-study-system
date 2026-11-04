package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.SysConfig;
import com.elderly.mapper.SysConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数配置服务：读取 sys_config 键值，内存缓存，后台修改后实时刷新。
 * TTS / AI 等模块通过本服务读取后台可配参数，未配置时回退 application.yml。
 */
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    private volatile ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        refresh();
    }

    /** 重新加载全部配置 */
    public synchronized void refresh() {
        List<SysConfig> list = sysConfigMapper.selectList(null);
        ConcurrentHashMap<String, String> fresh = new ConcurrentHashMap<>();
        for (SysConfig c : list) {
            fresh.put(c.getConfigKey(), c.getConfigValue());
        }
        // Publish the complete snapshot at once; feature flags and credentials
        // must not disappear temporarily while refresh is in progress.
        cache = fresh;
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String get(String key, String defaultValue) {
        String v = cache.get(key);
        return v == null || v.isEmpty() ? defaultValue : v;
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = cache.get(key);
        return v == null || v.isEmpty() ? defaultValue : "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public double getDouble(String key, double defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        String v = cache.get(key);
        if (v == null || v.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 全部配置（后台管理列表） */
    public List<SysConfig> list() {
        return sysConfigMapper.selectList(new QueryWrapper<SysConfig>().orderByAsc("id"));
    }

    /** 更新单个配置 */
    public synchronized void update(String key, String value) {
        SysConfig config = sysConfigMapper.selectOne(
                new QueryWrapper<SysConfig>().eq("config_key", key));
        if (config == null) {
            throw new IllegalArgumentException("配置项不存在: " + key);
        }
        config.setConfigValue(value);
        sysConfigMapper.updateById(config);
        cache.put(key, value);
    }

    /** 批量保存（不存在则插入，存在则更新） */
    public synchronized void saveBatch(List<SysConfig> configs) {
        if (configs == null) {
            return;
        }
        for (SysConfig c : configs) {
            if (c.getConfigKey() == null) {
                continue;
            }
            SysConfig existing = sysConfigMapper.selectOne(
                    new QueryWrapper<SysConfig>().eq("config_key", c.getConfigKey()));
            if (existing == null) {
                sysConfigMapper.insert(c);
            } else {
                existing.setConfigValue(c.getConfigValue());
                if (c.getConfigName() != null) {
                    existing.setConfigName(c.getConfigName());
                }
                if (c.getRemark() != null) {
                    existing.setRemark(c.getRemark());
                }
                sysConfigMapper.updateById(existing);
            }
            cache.put(c.getConfigKey(), c.getConfigValue());
        }
    }
}
