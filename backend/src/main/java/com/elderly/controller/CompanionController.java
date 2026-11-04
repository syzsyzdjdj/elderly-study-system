package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.AiCompanionScript;
import com.elderly.service.impl.CompanionScriptService;
import com.elderly.service.impl.DailyTipService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 数字陪伴话术：老人端取用，管理端维护
 */
@RestController
@RequestMapping("/api/companion")
@RequiredArgsConstructor
public class CompanionController {

    private final CompanionScriptService scriptService;
    private final DailyTipService dailyTipService;

    /** 老人端：今日贴心推荐（基于个人学习数据动态生成，Spark 推荐结果优先） */
    @GetMapping("/daily-tip")
    public Result<Map<String, Object>> dailyTip() {
        return Result.success(dailyTipService.build(SecurityUtil.getCurrentUserId()));
    }

    /** 老人端：全部启用话术（陪伴页分组展示与收听） */
    @GetMapping("/list")
    public Result<List<AiCompanionScript>> list() {
        return Result.success(scriptService.listEnabled());
    }

    /** 老人端：按场景取话术 */
    @GetMapping("/scene")
    public Result<List<AiCompanionScript>> scene(@RequestParam String scene) {
        return Result.success(scriptService.listByScene(scene));
    }

    /** 管理端：全部话术 */
    @GetMapping("/admin/list")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<AiCompanionScript>> adminList(@RequestParam(required = false) String scene) {
        return Result.success(scriptService.adminList(scene));
    }

    @PostMapping("/admin/save")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> save(@RequestBody AiCompanionScript script) {
        scriptService.save(script);
        return Result.success();
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        scriptService.delete(id);
        return Result.success();
    }
}
