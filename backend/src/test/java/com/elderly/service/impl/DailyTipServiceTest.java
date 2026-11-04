package com.elderly.service.impl;

import com.elderly.entity.CourseInfo;
import com.elderly.entity.StudyRecord;
import com.elderly.mapper.CourseCategoryMapper;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.StudyRecordMapper;
import com.elderly.service.AnalysisService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DailyTipServiceTest {
    @Test void popularityFallbackIsNotDescribedAsPersonalizedLearningInterest() {
        StudyRecordMapper records = mock(StudyRecordMapper.class);
        AnalysisService analysis = mock(AnalysisService.class);
        CourseInfoMapper courses = mock(CourseInfoMapper.class);
        StudyRecord record = new StudyRecord(); record.setCourseId(1L); record.setLastStudyTime(LocalDateTime.now());
        when(records.selectList(any())).thenReturn(List.of(record));
        CourseInfo popular = new CourseInfo(); popular.setId(2L); popular.setTitle("健康饮食");
        popular.setRecommendationSources(List.of("popular")); popular.setRecommendationReason("热门补充：最近90天有2位学员学习");
        when(analysis.recommendCourses(1L)).thenReturn(List.of(popular));
        Map<String, Object> result = new DailyTipService(records, courses, mock(CourseCategoryMapper.class), analysis).build(1L);
        assertEquals("热门课程补充", result.get("source"));
        assertEquals(List.of("popular"), result.get("recommendationSources"));
        assertTrue(result.get("tipText").toString().contains("2位学员"));
        assertFalse(result.get("tipText").toString().contains("根据您最近学习"));
    }

    @Test void emptyHybridResultsCannotBeBypassedWithOrdinaryCourseQueries() {
        StudyRecordMapper records = mock(StudyRecordMapper.class);
        AnalysisService analysis = mock(AnalysisService.class);
        CourseInfoMapper courses = mock(CourseInfoMapper.class);
        StudyRecord record = new StudyRecord(); record.setCourseId(1L); record.setLastStudyTime(LocalDateTime.now());
        when(records.selectList(any())).thenReturn(List.of(record));
        when(analysis.recommendCourses(1L)).thenReturn(List.of());
        DailyTipService service = new DailyTipService(records, courses, mock(CourseCategoryMapper.class), analysis);
        assertNull(service.build(1L).get("courseId"));
        when(records.selectList(any())).thenReturn(List.of());
        assertNull(service.build(1L).get("courseId"));
        verifyNoInteractions(courses);
    }
}
