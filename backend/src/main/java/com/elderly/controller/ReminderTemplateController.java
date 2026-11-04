package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.Reminder;
import com.elderly.entity.ReminderTemplate;
import com.elderly.service.ReminderTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时提醒模板：管理员统一维护（养老认证/学习打卡等），老人端一键选用
 */
@RestController
@RequestMapping("/api/reminder-template")
@RequiredArgsConstructor
public class ReminderTemplateController {

    private final ReminderTemplateService templateService;

    /** 老人端：启用中的模板列表 */
    @GetMapping("/enabled")
    public Result<List<ReminderTemplate>> enabled() {
        return Result.success(templateService.listEnabled());
    }

    /** 老人端：按模板为自己创建一条提醒 */
    @PostMapping("/use/{id}")
    public Result<Reminder> use(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BizException("未登录");
        return Result.success(templateService.useTemplate(id, userId));
    }

    /** 管理端：全部模板（含停用） */
    @GetMapping("/admin/list")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<ReminderTemplate>> adminList() {
        return Result.success(templateService.listAll());
    }

    /** 管理端：新增/修改模板 */
    @PostMapping("/admin/save")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<ReminderTemplate> save(@RequestBody ReminderTemplate template) {
        return Result.success(templateService.save(template));
    }

    /** 管理端：删除模板 */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success();
    }
}
