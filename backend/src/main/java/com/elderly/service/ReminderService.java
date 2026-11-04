package com.elderly.service;

import com.elderly.entity.Reminder;

import java.util.List;

public interface ReminderService {

    /** 查询当前用户所有提醒 */
    List<Reminder> list(Long userId);

    /** 新增提醒（自动计算 nextTriggerTime） */
    Reminder create(Reminder reminder);

    /** 修改提醒（自动重算 nextTriggerTime） */
    Reminder update(Reminder reminder);

    /** 删除提醒 */
    void delete(Long id, Long userId);

    /** 开关提醒（自动重算 nextTriggerTime） */
    void toggle(Long id, Long userId);

    /**
     * 获取当前用户到期的提醒（前端 poll 用）。
     * 规则：next_trigger_time <= now，且 enabled=1
     */
    List<Reminder> dueReminders(Long userId);

    /**
     * 标记某提醒已触发（防止重复弹窗），并推进到下一个周期
     */
    void markTriggered(Long id, Long userId);
}
