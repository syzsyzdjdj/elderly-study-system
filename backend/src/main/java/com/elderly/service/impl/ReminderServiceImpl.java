package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.Reminder;
import com.elderly.mapper.ReminderMapper;
import com.elderly.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    private final ReminderMapper reminderMapper;

    @Override
    public List<Reminder> list(Long userId) {
        return reminderMapper.selectList(
                new QueryWrapper<Reminder>().eq("user_id", userId)
                        .orderByAsc("next_trigger_time"));
    }

    @Override
    @Transactional
    public Reminder create(Reminder reminder) {
        reminder.setNextTriggerTime(calcNextTrigger(reminder));
        if (reminder.getEnabled() == null) reminder.setEnabled(1);
        reminderMapper.insert(reminder);
        return reminder;
    }

    @Override
    @Transactional
    public Reminder update(Reminder reminder) {
        Reminder exist = reminderMapper.selectById(reminder.getId());
        if (exist == null) throw new BizException("提醒不存在");
        if (!exist.getUserId().equals(reminder.getUserId())) {
            throw new BizException("无权操作");
        }
        reminder.setNextTriggerTime(calcNextTrigger(reminder));
        reminderMapper.updateById(reminder);
        return reminder;
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Reminder exist = reminderMapper.selectById(id);
        if (exist == null) throw new BizException("提醒不存在");
        if (!exist.getUserId().equals(userId)) throw new BizException("无权操作");
        reminderMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void toggle(Long id, Long userId) {
        Reminder exist = reminderMapper.selectById(id);
        if (exist == null) throw new BizException("提醒不存在");
        if (!exist.getUserId().equals(userId)) throw new BizException("无权操作");
        exist.setEnabled(exist.getEnabled() == 1 ? 0 : 1);
        if (exist.getEnabled() == 1) {
            exist.setNextTriggerTime(calcNextTrigger(exist));
        } else {
            exist.setNextTriggerTime(null);
        }
        reminderMapper.updateById(exist);
    }

    @Override
    public List<Reminder> dueReminders(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return reminderMapper.selectList(
                new QueryWrapper<Reminder>()
                        .eq("user_id", userId)
                        .eq("enabled", 1)
                        .isNotNull("next_trigger_time")
                        .le("next_trigger_time", now));
    }

    @Override
    @Transactional
    public void markTriggered(Long id, Long userId) {
        Reminder exist = reminderMapper.selectById(id);
        if (exist == null || !exist.getUserId().equals(userId)) return;
        // 推进到下次
        exist.setNextTriggerTime(calcNextTrigger(exist));
        // ONCE 触发后禁用
        if ("ONCE".equalsIgnoreCase(exist.getRemindType())) {
            exist.setEnabled(0);
            exist.setNextTriggerTime(null);
        }
        reminderMapper.updateById(exist);
    }

    // ======================== 定时扫描（兜底：防止服务器不重启时 nextTriggerTime 过期） ========================

    /**
     * 每分钟扫一次：给 next_trigger_time 过期但 enabled=1 的提醒重新计算 next_trigger_time。
     * 正常流程里 markTriggered 已经推进了 next_trigger_time，这里是防御性兜底。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void recomputeOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> overdue = reminderMapper.selectList(
                new QueryWrapper<Reminder>()
                        .eq("enabled", 1)
                        .isNotNull("next_trigger_time")
                        .lt("next_trigger_time", now.minusMinutes(2))); // 过期超过2分钟的
        for (Reminder r : overdue) {
            r.setNextTriggerTime(calcNextTrigger(r));
            if ("ONCE".equalsIgnoreCase(r.getRemindType())) {
                r.setEnabled(0);
                r.setNextTriggerTime(null);
            }
            reminderMapper.updateById(r);
            log.info("重新计算提醒 #{} nextTrigger -> {}", r.getId(), r.getNextTriggerTime());
        }
    }

    // ======================== 核心计算 ========================

    /** 根据提醒类型+时间，计算下一次触发时间 */
    static LocalDateTime calcNextTrigger(Reminder r) {
        LocalTime t = r.getRemindTime() == null ? LocalTime.of(9, 0) : r.getRemindTime();
        LocalDate today = LocalDate.now();
        LocalDate targetDate;

        switch (r.getRemindType() == null ? "DAILY" : r.getRemindType().toUpperCase()) {
            case "WEEKLY": {
                int dow = r.getDayOfWeek() == null ? 1 : r.getDayOfWeek();
                int javaDow = dow == 7 ? 7 : dow; // 我们约定 1=周一，兼容 7=周日
                targetDate = today.with(java.time.DayOfWeek.of(javaDow));
                if (!targetDate.isAfter(today)) {
                    targetDate = targetDate.plusWeeks(1);
                }
                break;
            }
            case "MONTHLY": {
                int dom = r.getDayOfMonth() == null ? 1 : r.getDayOfMonth();
                int day = Math.min(dom, today.lengthOfMonth());
                targetDate = today.withDayOfMonth(day);
                if (!targetDate.isAfter(today)) {
                    targetDate = targetDate.plusMonths(1);
                    day = Math.min(dom, targetDate.lengthOfMonth());
                    targetDate = targetDate.withDayOfMonth(day);
                }
                break;
            }
            case "ONCE": {
                // 找今天或明天，过了就设为 null（create 时前端会传一个明确的日期，这里兜底）
                targetDate = today;
                LocalDateTime candidate = LocalDateTime.of(targetDate, t);
                if (!candidate.isAfter(LocalDateTime.now())) {
                    return null; // 已经过了，标记为已完成
                }
                return candidate;
            }
            case "DAILY":
            default: {
                LocalDateTime todayAtTime = LocalDateTime.of(today, t);
                if (!todayAtTime.isAfter(LocalDateTime.now())) {
                    targetDate = today.plusDays(1);
                } else {
                    targetDate = today;
                }
                break;
            }
        }
        return LocalDateTime.of(targetDate, t);
    }
}
