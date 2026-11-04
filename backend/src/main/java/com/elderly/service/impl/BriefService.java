package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.StudyLog;
import com.elderly.mapper.StudyLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 当前登录用户的近七天学习周报，不使用平台群体分析结果。 */
@Service
@RequiredArgsConstructor
public class BriefService {
    private final StudyLogMapper studyLogMapper;

    public Map<String, Object> buildBrief(Long userId) {
        if (userId == null) throw new BizException("未登录");
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        Map<String, Object> week = firstMap(studyLogMapper.selectMaps(new QueryWrapper<StudyLog>()
                .select("COUNT(DISTINCT study_date) AS days", "COUNT(DISTINCT course_id) AS courses",
                        "COALESCE(SUM(study_time),0) AS duration")
                .eq("user_id", userId).gt("study_time", 0)
                .ge("study_date", start).le("study_date", today)));
        Map<String, Object> daily = firstMap(studyLogMapper.selectMaps(new QueryWrapper<StudyLog>()
                .select("COALESCE(SUM(study_time),0) AS duration")
                .eq("user_id", userId).gt("study_time", 0).eq("study_date", today)));
        long days = number(week.get("days"));
        long courses = number(week.get("courses"));
        long duration = number(week.get("duration"));
        long todayDuration = number(daily.get("duration"));
        String text = duration == 0
                ? "最近七天，您还没有学习记录。选一门感兴趣的课程，开始今天的学习吧！"
                : "这是您的学习周报。最近七天，您学习了" + days + "天，共学习" + courses
                    + "门课程，累计学习" + duration + "分钟。今天已学习" + todayDuration
                    + "分钟。每一次学习都是进步，继续加油！";
        Map<String, Object> result = new HashMap<>();
        result.put("startDate", start.toString());
        result.put("endDate", today.toString());
        result.put("studyDays", days);
        result.put("courseCount", courses);
        result.put("weekDuration", duration);
        result.put("todayDuration", todayDuration);
        result.put("text", text);
        return result;
    }

    private Map<String, Object> firstMap(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
