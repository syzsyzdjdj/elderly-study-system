package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.entity.PointRecord;
import com.elderly.entity.PointRule;
import com.elderly.entity.SysUser;
import com.elderly.mapper.PointRecordMapper;
import com.elderly.mapper.PointRuleMapper;
import com.elderly.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分服务：规则奖励（打卡/问答）、积分明细、连续打卡统计。
 * 课程完成奖励金额由课程决定，走 recordEarn 直接记录。
 */
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointRecordMapper pointRecordMapper;
    private final PointRuleMapper pointRuleMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 按积分规则发奖（受启用状态与每日次数上限约束）
     *
     * @return 实际发放的积分数（0 表示今日已达上限或规则停用）
     */
    @Transactional
    public int awardByRule(Long userId, String ruleCode, Long refId) {
        lockUser(userId);
        return awardByRuleLocked(userId, ruleCode, refId);
    }

    private int awardByRuleLocked(Long userId, String ruleCode, Long refId) {
        PointRule rule = pointRuleMapper.selectOne(
                new QueryWrapper<PointRule>().eq("rule_code", ruleCode));
        if (rule == null || rule.getEnabled() == null || rule.getEnabled() == 0) {
            return 0;
        }
        // 每日次数上限
        if (rule.getDailyLimit() != null && rule.getDailyLimit() > 0) {
            Long todayCount = pointRecordMapper.selectCount(
                    new QueryWrapper<PointRecord>()
                            .eq("user_id", userId)
                            .eq("change_type", ruleCode)
                            .ge("create_time", LocalDate.now().atStartOfDay()));
            if (todayCount >= rule.getDailyLimit()) {
                return 0;
            }
        }
        int points = rule.getPoints() == null ? 0 : rule.getPoints();
        recordEarn(userId, points, ruleCode, refId, rule.getRuleName());
        return points;
    }

    /**
     * 直接记录一笔收入积分（课程完成等金额由业务决定的场景），原子加分。
     */
    @Transactional
    public void recordEarn(Long userId, int points, String type, Long refId, String remark) {
        if (points <= 0) {
            return;
        }
        sysUserMapper.addPoints(userId, points);
        insertRecord(userId, points, type, refId, remark);
    }

    /**
     * 每日打卡
     */
    @Transactional
    public Map<String, Object> checkin(Long userId) {
        lockUser(userId);
        // 数据库行锁持有至事务提交，跨进程串行化同一用户的检查和发奖。
        Long todayCount = pointRecordMapper.selectCount(new QueryWrapper<PointRecord>()
                .eq("user_id", userId)
                .eq("change_type", "CHECKIN")
                .ge("create_time", LocalDate.now().atStartOfDay()));
        if (todayCount != null && todayCount > 0) {
            throw new BizException("今天已经打过卡啦，明天再来吧");
        }
        int earned = awardByRuleLocked(userId, "CHECKIN", null);
        Map<String, Object> result = new HashMap<>();
        result.put("earned", earned);
        result.put("totalPoints", getUserPoints(userId));
        result.put("continuousDays", continuousDays(userId));
        return result;
    }

    /** 今日是否已打卡 */
    public boolean hasCheckedInToday(Long userId) {
        Long count = pointRecordMapper.selectCount(new QueryWrapper<PointRecord>()
                .eq("user_id", userId)
                .eq("change_type", "CHECKIN")
                .ge("create_time", LocalDate.now().atStartOfDay()));
        return count != null && count > 0;
    }

    /** 连续打卡天数（从今天/昨天向前数连续有打卡记录的天数） */
    public int continuousDays(Long userId) {
        // 取最近 60 天的打卡日期（去重）
        List<Map<String, Object>> rows = pointRecordMapper.selectMaps(new QueryWrapper<PointRecord>()
                .select("DISTINCT DATE(create_time) AS d")
                .eq("user_id", userId)
                .eq("change_type", "CHECKIN")
                .ge("create_time", LocalDate.now().minusDays(60).atStartOfDay())
                .orderByDesc("d"));
        int streak = 0;
        LocalDate cursor = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            if (d instanceof java.sql.Date sqlDate) {
                dates.add(sqlDate.toLocalDate());
            } else if (d instanceof LocalDate localDate) {
                dates.add(localDate);
            }
        }
        // 今天没打卡则从昨天起算（不断签）
        if (dates.isEmpty()) {
            return 0;
        }
        if (!dates.contains(LocalDate.now())) {
            cursor = LocalDate.now().minusDays(1);
        }
        for (LocalDate d : dates) {
            if (d.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else if (d.isBefore(cursor)) {
                break;
            }
        }
        return streak;
    }

    /** 积分明细分页 */
    public Page<PointRecord> recordPage(Long userId, long page, long size, String type) {
        QueryWrapper<PointRecord> wrapper = new QueryWrapper<PointRecord>()
                .eq("user_id", userId)
                .orderByDesc("id");
        if (type != null && !type.isEmpty()) {
            wrapper.eq("change_type", type);
        }
        return pointRecordMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 全部积分规则（移动端展示规则说明） */
    public List<PointRule> listRules() {
        return pointRuleMapper.selectList(new QueryWrapper<PointRule>().orderByAsc("id"));
    }

    /** 今日已获得积分 */
    public int todayEarned(Long userId) {
        List<Map<String, Object>> rows = pointRecordMapper.selectMaps(new QueryWrapper<PointRecord>()
                .select("COALESCE(SUM(change_amount),0) AS s")
                .eq("user_id", userId)
                .gt("change_amount", 0)
                .ge("create_time", LocalDate.now().atStartOfDay()));
        if (rows.isEmpty() || rows.get(0).get("s") == null) {
            return 0;
        }
        return ((Number) rows.get(0).get("s")).intValue();
    }

    private int getUserPoints(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user == null || user.getPoints() == null ? 0 : user.getPoints();
    }

    private void insertRecord(Long userId, int amount, String type, Long refId, String remark) {
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setChangeAmount(amount);
        record.setBalance(getUserPoints(userId));
        record.setChangeType(type);
        record.setRemark(remark);
        record.setRefId(refId);
        record.setCreateTime(LocalDateTime.now());
        pointRecordMapper.insert(record);
    }

    private void lockUser(Long userId) {
        if (userId == null || sysUserMapper.lockPoints(userId) == null) {
            throw new BizException("用户不存在");
        }
    }
}
