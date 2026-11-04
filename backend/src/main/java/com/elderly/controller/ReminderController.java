package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.Reminder;
import com.elderly.common.SecurityUtil;
import com.elderly.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时语音提醒（老人端：自己的提醒）
 */
@RestController
@RequestMapping("/api/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    private Long curUserId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) throw new com.elderly.common.BizException("未登录");
        return id;
    }

    /** 当前用户所有提醒 */
    @GetMapping("/list")
    public Result<List<Reminder>> list() {
        return Result.success(reminderService.list(curUserId()));
    }

    /** 新增提醒 */
    @PostMapping
    public Result<Reminder> create(@RequestBody Reminder reminder) {
        reminder.setUserId(curUserId());
        return Result.success(reminderService.create(reminder));
    }

    /** 修改提醒 */
    @PutMapping
    public Result<Reminder> update(@RequestBody Reminder reminder) {
        reminder.setUserId(curUserId());
        return Result.success(reminderService.update(reminder));
    }

    /** 删除提醒 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reminderService.delete(id, curUserId());
        return Result.success();
    }

    /** 开关提醒 */
    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        reminderService.toggle(id, curUserId());
        return Result.success();
    }

    /**
     * 获取到期提醒（前端 poll 用）。
     * 到点的提醒 nextTriggerTime <= now。
     */
    @GetMapping("/due")
    public Result<List<Reminder>> due() {
        return Result.success(reminderService.dueReminders(curUserId()));
    }

    /** 标记已触发（前端收到并展示后调用，推进到下次） */
    @PostMapping("/{id}/mark-triggered")
    public Result<Void> markTriggered(@PathVariable Long id) {
        reminderService.markTriggered(id, curUserId());
        return Result.success();
    }
}
