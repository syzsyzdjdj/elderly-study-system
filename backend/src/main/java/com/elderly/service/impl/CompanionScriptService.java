package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.AiCompanionScript;
import com.elderly.mapper.AiCompanionScriptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 数字陪伴话术服务：移动端按场景取用，管理端 CRUD
 */
@Service
@RequiredArgsConstructor
public class CompanionScriptService {

    private final AiCompanionScriptMapper scriptMapper;

    /** 移动端：某场景下启用的话术 */
    public List<AiCompanionScript> listByScene(String scene) {
        return scriptMapper.selectList(new QueryWrapper<AiCompanionScript>()
                .eq("scene", scene).eq("enabled", 1).orderByAsc("sort"));
    }

    /** 移动端：全部启用话术（陪伴页分组展示） */
    public List<AiCompanionScript> listEnabled() {
        return scriptMapper.selectList(new QueryWrapper<AiCompanionScript>()
                .eq("enabled", 1).orderByAsc("scene", "sort"));
    }

    /** 管理端：全部话术 */
    public List<AiCompanionScript> adminList(String scene) {
        QueryWrapper<AiCompanionScript> wrapper = new QueryWrapper<AiCompanionScript>()
                .orderByAsc("scene", "sort");
        if (scene != null && !scene.isEmpty()) {
            wrapper.eq("scene", scene);
        }
        return scriptMapper.selectList(wrapper);
    }

    public void save(AiCompanionScript script) {
        if (script.getEnabled() == null) {
            script.setEnabled(1);
        }
        if (script.getSort() == null) {
            script.setSort(0);
        }
        if (script.getId() == null) {
            scriptMapper.insert(script);
        } else {
            scriptMapper.updateById(script);
        }
    }

    public void delete(Long id) {
        scriptMapper.deleteById(id);
    }
}
