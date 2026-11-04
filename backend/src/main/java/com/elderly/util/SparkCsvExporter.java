package com.elderly.util;

import com.elderly.config.SparkConfig;
import com.elderly.entity.AiQuestionRecord;
import com.elderly.entity.CourseCategory;
import com.elderly.entity.CourseInfo;
import com.elderly.entity.StudyLog;
import com.elderly.entity.SysUser;
import com.elderly.mapper.AiQuestionRecordMapper;
import com.elderly.mapper.CourseCategoryMapper;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.StudyLogMapper;
import com.elderly.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Properties;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在只读可重复读事务内导出业务输入和独立人工标签，共五个 CSV 到 spark-tmp/runs/<UUID>/in 快照。
 * 列名与 spark-analysis 模块 Schemas 严格一致；文本列清洗 \t \r \n。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SparkCsvExporter {

    private final SparkConfig sparkConfig;
    private final StudyLogMapper studyLogMapper;
    private final CourseInfoMapper courseInfoMapper;
    private final CourseCategoryMapper courseCategoryMapper;
    private final AiQuestionRecordMapper aiQuestionRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** 导出五个输入到 exportDir/in/，返回 in 目录路径 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public Path export() {
        populationSql(); // 在创建快照前验证来源配置。
        if (sparkConfig.getLookbackDays() < 1 || sparkConfig.getLookbackDays() > 3650) {
            throw new IllegalArgumentException("spark.lookback-days 必须在 1 到 3650 之间");
        }
        Path inDir = Paths.get(sparkConfig.getExportDir(), "runs", UUID.randomUUID().toString(), "in");
        LocalDate snapshotDate = LocalDate.now();
        try {
            Files.createDirectories(inDir);

            exportStudyLog(inDir.resolve("study_log.csv"), snapshotDate);
            exportCourseInfo(inDir.resolve("course_info.csv"));
            exportAiQuestion(inDir.resolve("ai_question.csv"), snapshotDate);
            exportCourseTrain(inDir.resolve("course_train.csv"));
            exportQuestionLabels(inDir.resolve("question_labels.csv"), snapshotDate);
            Properties metadata = new Properties();
            metadata.setProperty("runId", inDir.getParent().getFileName().toString());
            metadata.setProperty("dataScope", sparkConfig.getDataScope());
            metadata.setProperty("fromDate", snapshotDate.minusDays(sparkConfig.getLookbackDays() - 1).toString());
            metadata.setProperty("toDate", snapshotDate.toString());
            for (String file : new String[]{"study_log.csv", "course_info.csv", "ai_question.csv", "course_train.csv", "question_labels.csv"}) {
                try (java.util.stream.Stream<String> lines = Files.lines(inDir.resolve(file), StandardCharsets.UTF_8)) {
                    metadata.setProperty(file + ".rows", Long.toString(lines.count() - 1));
                }
            }
            metadata.setProperty("countDefinition", "hot=distinct user/course; trend=distinct user/day; duration=incremental minutes");
            try (java.io.Writer writer = Files.newBufferedWriter(inDir.getParent().resolve("snapshot.properties"), StandardCharsets.UTF_8)) {
                metadata.store(writer, "Immutable input snapshot; all tasks reuse five files including independent question labels");
            }
        } catch (IOException e) {
            throw new IllegalStateException("导出分析数据 CSV 失败: " + e.getMessage(), e);
        }
        log.info("分析数据 CSV 导出完成: {}", inDir.toAbsolutePath());
        return inDir;
    }

    private void exportStudyLog(Path file, LocalDate snapshotDate) throws IOException {
        List<StudyLog> logs = studyLogMapper.selectList(new QueryWrapper<StudyLog>()
                .inSql("user_id", populationSql()).inSql("course_id", "SELECT id FROM course_info WHERE status = 1")
                .between("study_date", snapshotDate.minusDays(sparkConfig.getLookbackDays() - 1), snapshotDate)
                .orderByAsc("id"));
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("user_id,course_id,study_time,progress,is_finish,study_date\n");
            for (StudyLog l : logs) {
                StringBuilder sb = new StringBuilder();
                sb.append(l.getUserId()).append(',')
                  .append(l.getCourseId()).append(',')
                  .append(l.getStudyTime() == null ? 0 : l.getStudyTime()).append(',')
                  .append(l.getProgress() == null ? 0 : l.getProgress()).append(',')
                  .append(l.getIsFinish() == null ? 0 : l.getIsFinish()).append(',')
                  .append(l.getStudyDate() == null ? "" : l.getStudyDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
                w.write(sb.toString());
                w.write('\n');
            }
        }
        log.info("导出 study_log {} 条", logs.size());
    }

    /** 标签与提问同一快照、同一来源；原文本改变后不再沿用历史标签。 */
    private void exportQuestionLabels(Path file, LocalDate snapshotDate) throws IOException {
        List<Map<String, Object>> labels = jdbc.queryForList(
                "SELECT q.id,q.question,l.question_text,l.category FROM ai_question_record q "
                + "JOIN analysis_question_label l ON q.id=l.question_id WHERE q.user_id IN (" + populationSql() + ") "
                + "AND q.create_time>=? AND q.create_time<? ORDER BY q.id",
                snapshotDate.minusDays(sparkConfig.getLookbackDays()-1).atStartOfDay(), snapshotDate.plusDays(1).atStartOfDay());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("id,question,category\n");
            for (Map<String, Object> row : labels) {
                if (!java.util.Objects.equals(row.get("question"), row.get("question_text"))) continue;
                w.write(row.get("id") + "," + escape((String) row.get("question")) + "," + escape((String) row.get("category")) + "\n");
            }
        }
    }

    private void exportCourseInfo(Path file) throws IOException {
        // 分类名映射（category_id -> name）
        Map<Long, String> categoryNames = new HashMap<>();
        for (CourseCategory c : courseCategoryMapper.selectList(null)) {
            categoryNames.put(c.getId(), c.getCategoryName());
        }
        List<CourseInfo> courses = courseInfoMapper.selectList(new QueryWrapper<CourseInfo>().eq("status", 1).orderByAsc("id"));
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("id,title,category_name\n");
            for (CourseInfo c : courses) {
                StringBuilder sb = new StringBuilder();
                sb.append(c.getId()).append(',')
                  .append(escape(c.getTitle())).append(',')
                  .append(escape(c.getCategoryId() == null ? "" : categoryNames.getOrDefault(c.getCategoryId(), "")));
                w.write(sb.toString());
                w.write('\n');
            }
        }
        log.info("导出 course_info {} 条", courses.size());
    }

    private void exportAiQuestion(Path file, LocalDate snapshotDate) throws IOException {
        List<AiQuestionRecord> records = aiQuestionRecordMapper.selectList(new QueryWrapper<AiQuestionRecord>()
                .inSql("user_id", populationSql())
                .ge("create_time", snapshotDate.minusDays(sparkConfig.getLookbackDays() - 1).atStartOfDay())
                .lt("create_time", snapshotDate.plusDays(1).atStartOfDay()).orderByAsc("id"));
        // 用户ID -> 省份映射（文本分类按地区维度统计用）
        Map<Long, String> userProvince = new HashMap<>();
        for (SysUser u : sysUserMapper.selectList(null)) {
            userProvince.put(u.getId(), u.getProvince() == null ? "" : u.getProvince());
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("id,user_id,question,province\n");
            for (AiQuestionRecord r : records) {
                StringBuilder sb = new StringBuilder();
                sb.append(r.getId()).append(',')
                  .append(r.getUserId() == null ? 0 : r.getUserId()).append(',')
                  .append(escape(r.getQuestion())).append(',')
                  .append(escape(userProvince.getOrDefault(r.getUserId(), "")));
                w.write(sb.toString());
                w.write('\n');
            }
        }
        log.info("导出 ai_question_record {} 条", records.size());
    }

    /**
     * 文本分类训练集：课程标题 + 顶级业务分类名。
     * 课程挂子分类时上溯到 parent_id 对应的顶级分类；未分类课程导出空 category（由 Spark 侧过滤）。
     */
    private void exportCourseTrain(Path file) throws IOException {
        Map<Long, CourseCategory> categoryById = new HashMap<>();
        for (CourseCategory c : courseCategoryMapper.selectList(null)) {
            categoryById.put(c.getId(), c);
        }
        List<CourseInfo> courses = courseInfoMapper.selectList(new QueryWrapper<CourseInfo>().eq("status", 1).orderByAsc("id"));
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("title,category\n");
            for (CourseInfo c : courses) {
                String topCategory = "";
                if (c.getCategoryId() != null) {
                    CourseCategory cat = categoryById.get(c.getCategoryId());
                    if (cat != null) {
                        Long parentId = cat.getParentId();
                        if (parentId != null && parentId > 0 && categoryById.containsKey(parentId)) {
                            topCategory = categoryById.get(parentId).getCategoryName();
                        } else {
                            topCategory = cat.getCategoryName();
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append(escape(c.getTitle())).append(',').append(escape(topCategory));
                w.write(sb.toString());
                w.write('\n');
            }
        }
        log.info("导出 course_train {} 条", courses.size());
    }

    /** 清洗 \t \r \n 为空格，含逗号/引号时按 CSV 规则加引号 */
    private String escape(String s) {
        if (s == null) {
            return "";
        }
        String cleaned = s.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (cleaned.contains(",") || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    private String populationSql() {
        String scope = sparkConfig.getDataScope();
        if (!"real".equals(scope) && !"mock".equals(scope)) {
            throw new IllegalArgumentException("spark.data-scope 只允许 real 或 mock，不允许混合数据");
        }
        return "SELECT id FROM sys_user WHERE status = 1 AND is_mock = " + ("mock".equals(scope) ? 1 : 0);
    }
}
