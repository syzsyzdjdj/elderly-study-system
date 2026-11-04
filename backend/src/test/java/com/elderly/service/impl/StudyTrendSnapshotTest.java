package com.elderly.service.impl;

import com.elderly.config.SparkConfig;
import com.elderly.entity.AnalysisStudyTrend;
import com.elderly.mapper.AnalysisStudyTrendMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class StudyTrendSnapshotTest {
    @TempDir Path root;
    @Mock AnalysisStudyTrendMapper trendMapper;
    @Mock JdbcTemplate jdbc;
    @Mock SparkConfig sparkConfig;
    @InjectMocks AnalysisServiceImpl service;

    @Test void returnsSavedBatchWindowRatherThanLastActiveDateOrCurrentConfig() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            String runId = UUID.randomUUID().toString();
            Path batch = Files.createDirectories(root.resolve("runs").resolve(runId));
            Files.write(batch.resolve("snapshot.properties"), Arrays.asList("fromDate=2026-07-24", "toDate=2026-10-21"), StandardCharsets.UTF_8);
            when(sparkConfig.getExportDir()).thenReturn(root.toString());
            when(trendMapper.selectList(any())).thenReturn(Collections.emptyList());
            Map<String,Object> metadata = new LinkedHashMap<>();
            metadata.put("runId",runId); metadata.put("dataScope","mock");
            when(jdbc.queryForList(anyString())).thenReturn(Collections.singletonList(metadata));
            Map<String,Object> result = service.getStudyTrendSnapshot();
            Map<?,?> actual = (Map<?,?>)result.get("metadata");
            assertEquals("mock",actual.get("dataScope"));
            assertEquals(runId,actual.get("runId"));
            assertEquals("2026-07-24",actual.get("fromDate"));
            assertEquals("2026-10-21",actual.get("toDate"));
            assertEquals(Collections.emptyList(),result.get("rows"));
        }
    }

    @Test void historicalRowsWithoutMetadataRemainAvailableAndUnattributed() throws Exception {
        try (AutoCloseable ignored = MockitoAnnotations.openMocks(this)) {
            AnalysisStudyTrend row = new AnalysisStudyTrend();
            when(trendMapper.selectList(any())).thenReturn(Collections.singletonList(row));
            when(jdbc.queryForList(anyString())).thenReturn(Collections.emptyList());
            Map<String,Object> result = service.getStudyTrendSnapshot();
            assertNull(result.get("metadata"));
            assertEquals(Collections.singletonList(row),result.get("rows"));
        }
    }
}
