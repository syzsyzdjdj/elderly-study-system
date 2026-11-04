package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.GovPolicy;
import com.elderly.entity.GovServiceEntity;
import com.elderly.service.impl.GovCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 政务服务：老人端按绑定地区查看办事入口与政策，管理端维护
 */
@RestController
@RequestMapping("/api/gov")
@RequiredArgsConstructor
public class GovController {

    private final GovCenterService govCenterService;

    /** 老人端：政务首页聚合（全国通用 + 本地专属分层） */
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        return Result.success(govCenterService.mobileHome(SecurityUtil.getCurrentUserId()));
    }

    // ---------------- 办事入口管理 ----------------

    @GetMapping("/admin/service/list")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<GovServiceEntity>> serviceList(@RequestParam(required = false) String scope) {
        return Result.success(govCenterService.adminServiceList(scope));
    }

    @PostMapping("/admin/service/save")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> saveService(@RequestBody GovServiceEntity entity) {
        govCenterService.saveService(entity);
        return Result.success();
    }

    @DeleteMapping("/admin/service/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteService(@PathVariable Long id) {
        govCenterService.deleteService(id);
        return Result.success();
    }

    // ---------------- 政策管理 ----------------

    @GetMapping("/admin/policy/list")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<GovPolicy>> policyList(@RequestParam(required = false) String level,
                                              @RequestParam(required = false) String province) {
        return Result.success(govCenterService.adminPolicyList(level, province));
    }

    @PostMapping("/admin/policy/save")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> savePolicy(@RequestBody GovPolicy entity) {
        govCenterService.savePolicy(entity);
        return Result.success();
    }

    @DeleteMapping("/admin/policy/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deletePolicy(@PathVariable Long id) {
        govCenterService.deletePolicy(id);
        return Result.success();
    }
}
