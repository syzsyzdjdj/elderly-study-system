package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.CourseCategory;
import com.elderly.entity.CourseInfo;
import com.elderly.entity.StudyRecord;
import com.elderly.mapper.CourseCategoryMapper;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.StudyRecordMapper;
import com.elderly.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 今日贴心推荐：基于老人个人学习数据动态生成陪伴话术。
 * 数据链路：study_record（学习统计）+ AnalysisService.recommendCourses 混合推荐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTipService {

    private final StudyRecordMapper studyRecordMapper;
    private final CourseInfoMapper courseInfoMapper;
    private final CourseCategoryMapper categoryMapper;
    private final AnalysisService analysisService;

    public Map<String, Object> build(Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("greeting", greeting());

        List<StudyRecord> records = studyRecordMapper.selectList(
                new QueryWrapper<StudyRecord>().eq("user_id", userId));

        // 已学完的课程 id（推荐时排除，避免推荐已完成的课）
        Set<Long> finishedIds = records.stream()
                .filter(r -> r.getIsFinish() != null && r.getIsFinish() == 1)
                .map(StudyRecord::getCourseId)
                .collect(Collectors.toSet());

        // 统一混合推荐（内容 + Spark 排序 + 热门补位），再次过滤已完成
        CourseInfo rec = analysisService.recommendCourses(userId).stream()
                .filter(c -> !finishedIds.contains(c.getId()))
                .findFirst()
                .orElse(null);

        String tipText;
        String source;
        String statText = null;
        Long tipCourseId = null;
        String tipCourseTitle = null;

        if (records.isEmpty()) {
            // 新手引导：还没有任何学习记录
            source = "新手引导";
            if (rec != null) {
                tipText = "欢迎加入老年学堂！建议您先从《" + rec.getTitle() + "》开始学，一步一步来，学会了生活更方便。";
                tipCourseId = rec.getId();
                tipCourseTitle = rec.getTitle();
            } else {
                tipText = "欢迎加入老年学堂！课程正在准备中，先逛逛分类看看有没有感兴趣的吧。";
            }
        } else {
            // 学习统计
            long courseCount = records.stream().map(StudyRecord::getCourseId).distinct().count();
            int totalMinutes = records.stream()
                    .mapToInt(r -> r.getStudyTime() == null ? 0 : r.getStudyTime())
                    .sum();
            statText = String.format("您已学过 %d 门课程，累计学习 %d 分钟，继续加油！", courseCount, totalMinutes);

            // 最近一次学习
            StudyRecord latest = records.stream()
                    .filter(r -> r.getLastStudyTime() != null)
                    .max(java.util.Comparator.comparing(StudyRecord::getLastStudyTime))
                    .orElse(null);
            long daysSince = latest == null ? 999
                    : Duration.between(latest.getLastStudyTime(), LocalDateTime.now()).toDays();

            if (daysSince >= 3) {
                // 回流唤起
                source = "回流唤起";
                tipText = "好几天没见到您啦，学堂的课程都在等您呢。";
            } else {
                // 推荐为空时保持为空，不绕过统一过滤再次推荐已接触的课程。
                List<String> sources = rec == null || rec.getRecommendationSources() == null
                        ? List.of() : rec.getRecommendationSources();
                source = sources.contains("content") ? "相似课程推荐"
                        : sources.contains("als") || sources.contains("item_cf") ? "学习偏好推荐"
                        : sources.contains("popular") ? "热门课程补充"
                        : sources.contains("latest") ? "最新课程补充" : "学习建议";
                tipText = rec != null && rec.getRecommendationReason() != null
                        ? rec.getRecommendationReason() + "。" : "继续保持学习的好习惯，";
            }

            if (rec != null) {
                tipText += "为您推荐《" + rec.getTitle() + "》，点一点，接着学吧。";
                tipCourseId = rec.getId();
                tipCourseTitle = rec.getTitle();
            } else {
                tipText += "您可以到课程列表里挑一门喜欢的课继续学习哦。";
            }
        }

        data.put("statText", statText);
        data.put("tipText", tipText);
        data.put("source", source);
        data.put("courseId", tipCourseId);
        data.put("courseTitle", tipCourseTitle);
        data.put("recommendationSources", rec == null ? List.of() : rec.getRecommendationSources());
        data.put("recommendationReason", rec == null ? null : rec.getRecommendationReason());
        return data;
    }

    /** 按时段问候 */
    private String greeting() {
        LocalTime t = LocalTime.now();
        if (t.isBefore(LocalTime.of(6, 0))) {
            return "夜深了，注意休息，明天再学也来得及。";
        }
        if (t.isBefore(LocalTime.of(9, 0))) {
            return "早上好！早餐后学一课，一天精神好。";
        }
        if (t.isBefore(LocalTime.of(12, 0))) {
            return "上午好！趁着记性好，来学一小节吧。";
        }
        if (t.isBefore(LocalTime.of(14, 0))) {
            return "中午好！午休前听一课，放松又长知识。";
        }
        if (t.isBefore(LocalTime.of(18, 0))) {
            return "下午好！喝杯热茶，边休息边听听课。";
        }
        return "晚上好！晚饭后和您一起复习一遍今天的内容吧。";
    }
}
