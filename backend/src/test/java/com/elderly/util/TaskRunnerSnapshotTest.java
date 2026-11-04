package com.elderly.util;

import com.elderly.config.SparkConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskRunnerSnapshotTest {
    @TempDir Path dir;
    @Test void batchExportsOnceAndReusesSameInputForEveryTask() throws Exception {
        SparkCsvExporter exporter = mock(SparkCsvExporter.class);
        YarnSubmitUtil yarn = mock(YarnSubmitUtil.class);
        Path input = dir.resolve("11111111-1111-1111-1111-111111111111/in");
        Path tsv = dir.resolve("empty.tsv"); Files.writeString(tsv, "study_date\tstudy_count\tstudy_duration_min\n");
        when(exporter.export()).thenReturn(input);
        when(yarn.submitSnapshot(eq("study_trend"), anyList(), eq(input))).thenReturn(Map.of("study_trend", tsv));
        SparkConfig config = new SparkConfig(); config.setMode("yarn");
        TaskRunner runner = new TaskRunner(config, exporter, yarn);
        runner.withSnapshot(() -> {
            runner.runTask("study_trend"); runner.runTask("study_trend");
            assertEquals(input.getParent().getFileName().toString(), runner.snapshotId());
            return null;
        });
        verify(exporter, times(1)).export();
        verify(yarn, times(2)).submitSnapshot(eq("study_trend"), anyList(), eq(input));
        assertEquals("", runner.snapshotId());
        assertThrows(IllegalStateException.class, () -> runner.withSnapshot(() -> { throw new IllegalStateException("test"); }));
        assertEquals("", runner.snapshotId());
    }
    @Test void snapshotScriptDoesNotReadJdbcOrDeleteSharedInputs() {
        SparkConfig config = new SparkConfig(); config.getYarn().setJdbcUrl("should-not-appear");
        YarnSubmitUtil yarn = new YarnSubmitUtil(config);
        String script = yarn.buildRemoteScript("text_classify", List.of("text_classify", "text_classify_metrics"), false,
                "/root/spark-apps/runs/11111111-1111-1111-1111-111111111111",
                "hdfs://hadoop101:9000/user/root/spark-apps/runs/11111111-1111-1111-1111-111111111111");
        assertFalse(script.contains("--jdbc-url")); assertFalse(script.contains("dfs -rm"));
        assertFalse(script.contains("rm -f")); assertFalse(script.contains("safemode leave"));
        assertTrue(script.contains("text_classify_metrics/part-*"));
        assertTrue(script.contains("--conf spark.eventLog.enabled=true"));
        assertFalse(script.contains("spark.eventLog.dir=")); // 使用集群与History Server约定的目录
    }

    @Test void localModeRunsRealSparkAndParsesResultsWithoutYarn() throws Exception {
        Path input = Files.createDirectories(dir.resolve("local-run/in"));
        Files.writeString(input.resolve("study_log.csv"),
                "user_id,course_id,study_time,progress,is_finish,study_date\n"
                + "1,10,120,50,0,2026-11-03\n1,10,60,80,0,2026-11-03\n"
                + "2,10,180,100,1,2026-11-03\n2,20,120,50,0,2026-11-03\n");
        Files.writeString(input.resolve("course_info.csv"),
                "id,title,category_name\n10,手机支付,手机\n20,视频通话,手机\n");
        SparkCsvExporter exporter = mock(SparkCsvExporter.class);
        YarnSubmitUtil yarn = mock(YarnSubmitUtil.class);
        when(exporter.export()).thenReturn(input);
        SparkConfig config = new SparkConfig(); config.setMasterUrl("local[1]");
        TaskRunner runner = new TaskRunner(config, exporter, yarn);
        runner.withSnapshot(() -> {
            var result = runner.runTask("course_hot");
            assertEquals(2, result.get("course_hot").size());
            assertEquals("10", result.get("course_hot").get(0).get("course_id"));
            assertEquals("2", result.get("course_hot").get(0).get("study_count"));
            assertEquals("2", result.get("course_category").get(0).get("study_count"));
            // A second task must create a fresh Spark context after the first has stopped.
            assertFalse(runner.runTask("study_trend").get("study_trend").isEmpty());
            return null;
        });
        verifyNoInteractions(yarn);
        verify(exporter, times(1)).export();
    }

    @Test void rejectsUnsupportedModeAndRemoteMaster() {
        SparkCsvExporter exporter = mock(SparkCsvExporter.class);
        YarnSubmitUtil yarn = mock(YarnSubmitUtil.class);
        when(exporter.export()).thenReturn(dir.resolve("run/in"));
        SparkConfig config = new SparkConfig(); config.setMode("invalid");
        TaskRunner runner = new TaskRunner(config, exporter, yarn);
        assertThrows(com.elderly.common.BizException.class, () -> runner.runTask("course_hot"));
        config.setMode("local"); config.setMasterUrl("spark://remote:7077");
        assertThrows(com.elderly.common.BizException.class, () -> runner.runTask("course_hot"));
        verifyNoInteractions(yarn);
    }
}
