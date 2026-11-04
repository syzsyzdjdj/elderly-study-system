package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.GovPolicy;
import com.elderly.entity.GovServiceEntity;
import com.elderly.entity.SysUser;
import com.elderly.mapper.GovPolicyMapper;
import com.elderly.mapper.GovServiceMapper;
import com.elderly.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 政务服务中心：
 * 移动端按用户绑定地区分层返回"全国通用 + 本地专属"的办事入口与政策；
 * 管理端提供政策/办事入口 CRUD。
 */
@Service
@RequiredArgsConstructor
public class GovCenterService {

    private final GovServiceMapper serviceMapper;
    private final GovPolicyMapper policyMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 移动端政务首页聚合数据
     */
    public Map<String, Object> mobileHome(Long userId) {
        SysUser user = userId == null ? null : sysUserMapper.selectById(userId);
        String province = user == null ? null : user.getProvince();
        String city = user == null ? null : user.getCity();

        // 办事入口：全国 + 本省/本市
        List<GovServiceEntity> nationalServices = serviceMapper.selectList(
                new QueryWrapper<GovServiceEntity>()
                        .eq("scope", "NATIONAL").eq("status", 1).orderByAsc("sort"));
        QueryWrapper<GovServiceEntity> localWrapper = new QueryWrapper<GovServiceEntity>()
                .eq("scope", "LOCAL").eq("status", 1).orderByAsc("sort");
        if (province != null && !province.isEmpty()) {
            // 省级服务可供本省所有城市使用；市级服务只可供对应城市使用。
            // 不能使用 province OR city，否则会展示同省其他城市的专属入口。
            localWrapper.eq("province", province)
                    .and(w -> w.isNull("city")
                            .or(city != null && !city.isEmpty(), w2 -> w2.eq("city", city)));
        } else {
            // 未绑定地区时本地入口不展示，引导先绑定地区
            localWrapper.eq("1", 0).eq("1", 1);
        }
        List<GovServiceEntity> localServices = serviceMapper.selectList(localWrapper);

        // 政策：全国通用 + 本省/本市（分层展示）
        List<GovPolicy> nationalPolicies = policyMapper.selectList(
                new QueryWrapper<GovPolicy>()
                        .eq("policy_level", "NATIONAL").eq("status", 1)
                        .orderByDesc("publish_time"));
        QueryWrapper<GovPolicy> localPolicyWrapper = new QueryWrapper<GovPolicy>()
                .ne("policy_level", "NATIONAL").eq("status", 1)
                .orderByDesc("publish_time");
        if (province != null && !province.isEmpty()) {
            localPolicyWrapper.eq("province", province);
        } else {
            localPolicyWrapper.eq("1", 0).eq("1", 1);
        }
        List<GovPolicy> localPolicies = policyMapper.selectList(localPolicyWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("region", province == null ? null : province + (city == null ? "" : " " + city));
        result.put("nationalServices", nationalServices);
        result.put("localServices", localServices);
        result.put("nationalPolicies", nationalPolicies);
        result.put("localPolicies", localPolicies);
        return result;
    }

    // ---------------- 管理端：办事入口 CRUD ----------------

    public List<GovServiceEntity> adminServiceList(String scope) {
        QueryWrapper<GovServiceEntity> wrapper = new QueryWrapper<GovServiceEntity>().orderByAsc("scope", "sort");
        if (scope != null && !scope.isEmpty()) {
            wrapper.eq("scope", scope);
        }
        return serviceMapper.selectList(wrapper);
    }

    public void saveService(GovServiceEntity entity) {
        if (entity.getName() == null || entity.getServiceUrl() == null) {
            throw new IllegalArgumentException("服务名称与直达链接不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSort() == null) {
            entity.setSort(0);
        }
        if (entity.getScope() == null) {
            entity.setScope("NATIONAL");
        }
        if (entity.getId() == null) {
            serviceMapper.insert(entity);
        } else {
            serviceMapper.updateById(entity);
        }
    }

    public void deleteService(Long id) {
        serviceMapper.deleteById(id);
    }

    // ---------------- 管理端：政策 CRUD ----------------

    public List<GovPolicy> adminPolicyList(String level, String province) {
        QueryWrapper<GovPolicy> wrapper = new QueryWrapper<GovPolicy>().orderByDesc("publish_time");
        if (level != null && !level.isEmpty()) {
            wrapper.eq("policy_level", level);
        }
        if (province != null && !province.isEmpty()) {
            wrapper.eq("province", province);
        }
        return policyMapper.selectList(wrapper);
    }

    public void savePolicy(GovPolicy entity) {
        if (entity.getTitle() == null) {
            throw new IllegalArgumentException("政策标题不能为空");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getPolicyLevel() == null) {
            entity.setPolicyLevel("NATIONAL");
        }
        if (entity.getId() == null) {
            policyMapper.insert(entity);
        } else {
            policyMapper.updateById(entity);
        }
    }

    public void deletePolicy(Long id) {
        policyMapper.deleteById(id);
    }
}
