import com.elderly.config.SparkConfig;
import com.elderly.util.YarnSubmitUtil;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 独立模拟推荐验证：只增加有明确标记的测试账号，不替换线上分析结果。项目根目录运行。 */
public class RecommendationSamples {
    private static final String PREFIX = "recsample_v1_";
    private static final int USERS = 30;
    private static final int DAYS = 30;

    public static void main(String[] args) throws Exception {
        Properties p = com.elderly.util.ToolConfiguration.load();
        DriverManagerDataSource ds = new DriverManagerDataSource(required(p, "spring.datasource.url"),
                required(p, "spring.datasource.username"), required(p, "spring.datasource.password"));
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        if (args.length == 0 || args[0].equals("inspect")) {
            System.out.println("population=" + jdbc.queryForList("SELECT is_mock,COUNT(*) AS users FROM sys_user GROUP BY is_mock"));
            System.out.println("onlineCourses=" + jdbc.queryForObject("SELECT COUNT(*) FROM course_info WHERE status=1", Integer.class));
            System.out.println("userRoleCount=" + jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_key='user'", Integer.class));
            System.out.println("sampleUsers=" + jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE LEFT(username,?)=?", Integer.class, PREFIX.length(), PREFIX));
            System.out.println("userColumns=" + jdbc.queryForList("SELECT COLUMN_NAME,IS_NULLABLE,COLUMN_DEFAULT FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sys_user'"));
            return;
        }
        if (args[0].equals("seed")) {
            new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(status -> { seed(jdbc); return null; });
        } else if (args[0].equals("export")) {
            Path input = export(jdbc);
            System.out.println("input=" + input.toAbsolutePath());
        } else if (args[0].equals("run") && args.length == 2) {
            Path input = Path.of(args[1]).toAbsolutePath();
            Properties metadata = new Properties();
            try (Reader r = Files.newBufferedReader(input.getParent().resolve("snapshot.properties"), StandardCharsets.UTF_8)) { metadata.load(r); }
            if (!"mock".equals(metadata.getProperty("dataScope"))) throw new IllegalArgumentException("Only mock snapshots accepted");
            SparkConfig config = new SparkConfig(); config.setDataScope("mock");
            SparkConfig.SparkYarnConfig y = config.getYarn();
            y.setHost(p.getProperty("spark.yarn.host", y.getHost()));
            y.setUser(p.getProperty("spark.yarn.user", y.getUser()));
            y.setSshKey(p.getProperty("spark.yarn.ssh-key", y.getSshKey()));
            y.setRemoteDir(p.getProperty("spark.yarn.remote-dir", y.getRemoteDir()));
            y.setHdfsBase(p.getProperty("spark.yarn.hdfs-base", y.getHdfsBase()));
            y.setJarPath(p.getProperty("spark.yarn.jar-path", y.getJarPath()));
            YarnSubmitUtil yarn = new YarnSubmitUtil(config);
            for (String task : List.of("item_cf", "als_recommend")) {
                Map<String, Path> outputs = yarn.submitSnapshot(task, List.of(task, task + "_metrics"), input);
                System.out.println("task=" + task);
                for (Map.Entry<String, Path> entry : outputs.entrySet()) {
                    if (entry.getKey().endsWith("_metrics")) Files.readAllLines(entry.getValue(), StandardCharsets.UTF_8).forEach(System.out::println);
                    else try (var lines = Files.lines(entry.getValue(), StandardCharsets.UTF_8)) { System.out.println("recommendations=" + Math.max(0, lines.count() - 1)); }
                }
            }
        } else throw new IllegalArgumentException("inspect | seed | export | run <mock-input-directory>");
    }

    private static void seed(JdbcTemplate jdbc) {
        List<Long> courses = jdbc.queryForList("SELECT id FROM course_info WHERE status=1 ORDER BY id", Long.class);
        if (courses.size() < 5) throw new IllegalStateException("At least five online courses required");
        Long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_key='user' LIMIT 1", Long.class);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE LEFT(username,?)=?", Integer.class, PREFIX.length(), PREFIX);
        if (existing != null && existing > 0) {
            System.out.println("No duplicate generation; existing sample accounts=" + existing);
            return;
        }
        String password = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
        LocalDate today = LocalDate.now();
        int groups = Math.min(3, courses.size() / 5);
        for (int u = 0; u < USERS; u++) {
            String username = PREFIX + String.format(Locale.ROOT, "%02d", u + 1);
            jdbc.update("INSERT INTO sys_user(username,password,nickname,role_id,points,province,city,district,font_scale,high_contrast,voice,status,is_mock,create_time,update_time) "
                            + "VALUES(?,?,?,?,0,'北京市','北京市','东城区',100,0,'default',1,1,?,?)",
                    username, password, "推荐模拟学员" + (u + 1), role, LocalDateTime.now(), LocalDateTime.now());
            Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=? AND is_mock=1", Long.class, username);
            int group = u % groups;
            int elective = (u / groups) % 3;
            List<Long> training = List.of(courses.get(group * 5), courses.get(group * 5 + 1), courses.get(group * 5 + 2 + elective));
            long heldOut = courses.get(group * 5 + 2 + (elective + 1) % 3);
            List<Object[]> rows = new ArrayList<>();
            for (int day = 0; day < DAYS; day++) {
                boolean test = day >= 24;
                long course = test ? heldOut : training.get(day % 3);
                int progress = test ? Math.min(100, (day - 23) * 20) : Math.min(100, 20 + (day / 3) * 15);
                LocalDate date = today.minusDays(DAYS - 1L - day);
                rows.add(new Object[]{id, course, 2 + day % 3, progress, progress == 100 ? 1 : 0, date, date.atTime(9 + u % 3, day % 60)});
            }
            jdbc.batchUpdate("INSERT INTO study_log(user_id,course_id,study_time,progress,is_finish,study_date,create_time) VALUES(?,?,?,?,?,?,?)", rows);
        }
        System.out.println("Created mock users=" + USERS + "; logs=" + USERS * DAYS + "; held-out users=30; held-out positive pairs=30");
    }

    private static Path export(JdbcTemplate jdbc) throws IOException {
        Path root = Path.of("spark-tmp", "runs", UUID.randomUUID().toString());
        Path input = Files.createDirectories(root.resolve("in"));
        String population = "SELECT id FROM sys_user WHERE is_mock=1 AND status=1 AND LEFT(username,"
                + PREFIX.length() + ")='" + PREFIX + "'";
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("study_log.csv", csv(jdbc, input.resolve("study_log.csv"),
                "user_id,course_id,study_time,progress,is_finish,study_date",
                "SELECT user_id,course_id,study_time,progress,is_finish,study_date FROM study_log "
                        + "WHERE user_id IN (" + population + ") AND course_id IN (SELECT id FROM course_info WHERE status=1) "
                        + "AND study_date>=? AND study_date<=? ORDER BY study_date,user_id,course_id",
                LocalDate.now().minusDays(DAYS - 1), LocalDate.now()));
        counts.put("course_info.csv", csv(jdbc, input.resolve("course_info.csv"), "id,title,category_name",
                "SELECT c.id,c.title,COALESCE(cat.category_name,'未分类') FROM course_info c "
                        + "LEFT JOIN course_category cat ON cat.id=c.category_id WHERE c.status=1 ORDER BY c.id"));
        counts.put("course_train.csv", csv(jdbc, input.resolve("course_train.csv"), "title,category",
                "SELECT c.title,COALESCE(parent.category_name,cat.category_name,'未分类') FROM course_info c "
                        + "LEFT JOIN course_category cat ON cat.id=c.category_id LEFT JOIN course_category parent ON parent.id=cat.parent_id "
                        + "WHERE c.status=1 ORDER BY c.id"));
        Files.writeString(input.resolve("ai_question.csv"), "id,user_id,question,province\n", StandardCharsets.UTF_8);
        Files.writeString(input.resolve("question_labels.csv"), "id,question,category\n", StandardCharsets.UTF_8);
        counts.put("ai_question.csv", 0L); counts.put("question_labels.csv", 0L);
        Properties metadata = new Properties();
        metadata.setProperty("runId", root.getFileName().toString()); metadata.setProperty("dataScope", "mock");
        metadata.setProperty("generator", "recommendation-samples-v1");
        metadata.setProperty("population", PREFIX); metadata.setProperty("lookbackDays", Integer.toString(DAYS));
        metadata.setProperty("fromDate", LocalDate.now().minusDays(DAYS - 1).toString());
        metadata.setProperty("toDate", LocalDate.now().toString());
        counts.forEach((name, count) -> metadata.setProperty(name + ".rows", count.toString()));
        try (Writer writer = Files.newBufferedWriter(root.resolve("snapshot.properties"), StandardCharsets.UTF_8)) {
            metadata.store(writer, "Synthetic demonstration only; never use as real-user effectiveness evidence");
        }
        System.out.println("dataScope=mock; rows=" + counts);
        return input;
    }

    private static long csv(JdbcTemplate jdbc, Path file, String header, String sql, Object... args) throws IOException {
        long[] count = {0};
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(header); writer.newLine();
            jdbc.query(sql, rs -> {
                try {
                    int columns = rs.getMetaData().getColumnCount();
                    for (int col = 1; col <= columns; col++) {
                        if (col > 1) writer.write(',');
                        Object object = rs.getObject(col);
                        String value = object == null ? "" : object.toString().replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
                        writer.write("\"" + value.replace("\"", "\"\"") + "\"");
                    }
                    writer.newLine(); count[0]++;
                } catch (IOException e) { throw new UncheckedIOException(e); }
            }, args);
        }
        return count[0];
    }

    private static String required(Properties p, String name) {
        return Objects.requireNonNull(p.getProperty(name), "Missing application property " + name);
    }
}
