package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.StudyLog;
import com.elderly.mapper.StudyLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BriefServiceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyQueryIsRestrictedToCurrentUserAndReportingPeriod() {
        StudyLogMapper mapper = mock(StudyLogMapper.class);
        when(mapper.selectMaps(any())).thenReturn(
                List.of(Map.<String, Object>of("days", 2L, "courses", 3L, "duration", 45L)),
                List.of(Map.<String, Object>of("duration", 10L)));
        Map<String, Object> report = new BriefService(mapper).buildBrief(42L);
        assertEquals(2L, report.get("studyDays"));
        assertEquals(3L, report.get("courseCount"));
        assertEquals(45L, report.get("weekDuration"));
        assertEquals(10L, report.get("todayDuration"));
        assertFalse(report.containsKey("totalUsers"));
        assertFalse(report.containsKey("hotCourses"));
        ArgumentCaptor<QueryWrapper<StudyLog>> queries = ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(mapper, times(2)).selectMaps(queries.capture());
        for (QueryWrapper<StudyLog> query : queries.getAllValues()) {
            assertTrue(query.getSqlSegment().contains("user_id ="));
            assertTrue(query.getParamNameValuePairs().containsValue(42L));
            assertTrue(query.getParamNameValuePairs().containsValue(LocalDate.now()));
        }
        assertTrue(queries.getAllValues().get(0).getSqlSegment().contains("study_date <="));
        assertTrue(queries.getAllValues().get(0).getParamNameValuePairs()
                .containsValue(LocalDate.now().minusDays(6)));
    }

    @Test
    void missingIdentityCannotFallBackToAllUsers() {
        StudyLogMapper mapper = mock(StudyLogMapper.class);
        assertThrows(BizException.class, () -> new BriefService(mapper).buildBrief(null));
        verifyNoInteractions(mapper);
    }

    @Test
    void noStudyDataProducesPersonalEmptyReport() {
        StudyLogMapper mapper = mock(StudyLogMapper.class);
        when(mapper.selectMaps(any())).thenReturn(List.of());
        Map<String, Object> report = new BriefService(mapper).buildBrief(9L);
        assertEquals(0L, report.get("weekDuration"));
        assertEquals(0L, report.get("studyDays"));
        assertTrue(report.get("text").toString().contains("还没有学习记录"));
    }
}
