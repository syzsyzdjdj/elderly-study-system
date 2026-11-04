package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.PointRecord;
import com.elderly.entity.PointRule;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysUserMapper;
import com.elderly.service.impl.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学习积分：打卡、积分明细、积分规则
 */
@RestController
@RequestMapping("/api/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    private final SysUserMapper sysUserMapper;

    private Long userId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) {
            throw new BizException("请先登录");
        }
        return id;
    }

    /** 积分首页汇总：总积分、今日已得、今日是否已打卡、连续天数 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Long userId = userId();
        SysUser user = sysUserMapper.selectById(userId);
        Map<String, Object> map = new HashMap<>();
        map.put("totalPoints", user == null || user.getPoints() == null ? 0 : user.getPoints());
        map.put("todayEarned", pointService.todayEarned(userId));
        map.put("checkedIn", pointService.hasCheckedInToday(userId));
        map.put("continuousDays", pointService.continuousDays(userId));
        return Result.success(map);
    }

    /** 每日打卡 */
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin() {
        return Result.success(pointService.checkin(userId()));
    }

    /** 积分明细分页 */
    @GetMapping("/records")
    public Result<Page<PointRecord>> records(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String type) {
        return Result.success(pointService.recordPage(userId(), page, size, type));
    }

    /** 积分规则说明 */
    @GetMapping("/rules")
    public Result<List<PointRule>> rules() {
        return Result.success(pointService.listRules());
    }
}
