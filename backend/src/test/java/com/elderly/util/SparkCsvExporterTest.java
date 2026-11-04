package com.elderly.util;

import com.elderly.config.SparkConfig;
import com.elderly.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SparkCsvExporterTest {
    @TempDir Path root;
    @Test void snapshotIncludesOnlyCurrentUnchangedHumanLabelsFromChosenPopulation() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:export-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,status INT,is_mock INT)");
        jdbc.execute("CREATE TABLE ai_question_record(id BIGINT PRIMARY KEY,user_id BIGINT,question VARCHAR(200),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE analysis_question_label(question_id BIGINT PRIMARY KEY,question_text VARCHAR(200),category VARCHAR(64))");
        jdbc.update("INSERT INTO sys_user VALUES(1,1,0),(2,1,1),(3,0,0)");
        for (int id=1; id<=5; id++) {
            jdbc.update("INSERT INTO ai_question_record VALUES(?,?,?,?)", id, id==2 ? 2 : id==3 ? 3 : 1,
                    id==1 ? "微信,支付\"操作\"" : "提问"+id, id==5 ? LocalDateTime.now().minusDays(100) : LocalDateTime.now());
            jdbc.update("INSERT INTO analysis_question_label VALUES(?,?,?)", id,
                    id==1 ? "微信,支付\"操作\"" : id==4 ? "修改前文本" : "提问"+id, "支付");
        }
        SparkConfig config = new SparkConfig(); config.setExportDir(root.toString());
        SparkCsvExporter exporter = new SparkCsvExporter(config, mock(StudyLogMapper.class), mock(CourseInfoMapper.class),
                mock(CourseCategoryMapper.class), mock(AiQuestionRecordMapper.class), mock(SysUserMapper.class), jdbc);
        Path input = exporter.export();
        List<String> labels = Files.readAllLines(input.resolve("question_labels.csv"), StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("id,question,category", "1,\"微信,支付\"\"操作\"\"\",支付"), labels);
        Properties metadata = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(input.getParent().resolve("snapshot.properties"), StandardCharsets.UTF_8)) { metadata.load(reader); }
        assertEquals("1", metadata.getProperty("question_labels.csv.rows"));
        assertEquals("real", metadata.getProperty("dataScope"));
        config.setDataScope("mock");
        Path mockInput = exporter.export();
        assertEquals(Arrays.asList("id,question,category", "2,提问2,支付"), Files.readAllLines(mockInput.resolve("question_labels.csv"), StandardCharsets.UTF_8));
        assertNotEquals(input, mockInput);
    }
}
