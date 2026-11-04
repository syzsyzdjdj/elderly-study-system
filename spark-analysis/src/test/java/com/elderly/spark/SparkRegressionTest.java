package com.elderly.spark;

import com.elderly.spark.task.*;
import com.elderly.spark.wordseg.JiebaTokenizer;
import com.huaban.analysis.jieba.JiebaSegmenter;
import org.apache.spark.sql.*;
import org.junit.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** 运行：mvn -f spark-analysis/pom.xml test -Dspark.version=3.5.1（JDK17）。 */
public class SparkRegressionTest {
    private static SparkSession spark;
    private static Path root;
    @BeforeClass public static void start() throws Exception {
        root = Files.createTempDirectory(Paths.get("target"), "spark-regression-");
        spark = SparkSession.builder().master("local[2]").appName("analysis-regression")
                .config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2")
                .config("spark.sql.legacy.timeParserPolicy", "CORRECTED")
                .config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.local.dir", root.resolve("tmp").toAbsolutePath().toString()).getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }
    @AfterClass public static void stop() { if (spark != null) spark.stop(); }

    @Test public void incrementalSavesDoNotInflateCourseCompletionOrDailyDuration() {
        List<Row> rows = Arrays.asList(
                RowFactory.create(1L, 1L, 1, 20, 0, "2026-10-01"),
                RowFactory.create(1L, 1L, 1, 100, 1, "2026-10-01"),
                RowFactory.create(1L, 2L, 0, 30, 0, "2026-10-01"),
                RowFactory.create(2L, 1L, -1, 20, 0, "2026-10-01"),
                RowFactory.create(3L, 1L, 1, 101, 0, "2026-10-01"),
                RowFactory.create(4L, 1L, 1, 20, 0, "2026-02-30"),
                RowFactory.create(5L, 1L, 0, 0, 0, "2026-10-01"));
        Dataset<Row> clean = StudyMetrics.clean(spark.createDataFrame(rows, Schemas.STUDY_LOG));
        assertEquals(3, clean.count());
        Row feature = StudyMetrics.userFeatures(clean).first();
        assertEquals(1L, ((Number) feature.getAs("study_freq")).longValue());
        assertEquals(2.0, ((Number) feature.getAs("avg_study_time")).doubleValue(), 1e-8);
        assertEquals(0.5, ((Number) feature.getAs("finish_rate")).doubleValue(), 1e-8);
    }

    @Test public void emptyHotInputProducesEmptyResultsInsteadOfNullFailure() throws Exception {
        Path input = Files.createDirectories(root.resolve("empty"));
        Files.write(input.resolve("study_log.csv"), Collections.singletonList("user_id,course_id,study_time,progress,is_finish,study_date"), StandardCharsets.UTF_8);
        Files.write(input.resolve("course_info.csv"), Collections.singletonList("id,title,category_name"), StandardCharsets.UTF_8);
        Args args = args(input, root.resolve("empty-out"), "course_hot");
        new CourseHotTask().run(spark, args);
        assertEquals(1, Files.readAllLines(root.resolve("empty-out/course_category/part-00000")).size());
    }

    @Test public void tokenizerKeepsAppNamesAndCountsEachQuestionOnce() throws Exception {
        String doc = JiebaTokenizer.toDocument("WiFi APP 12306 微信微信", new JiebaSegmenter());
        assertTrue(doc, doc.contains("wifi")); assertTrue(doc, doc.contains("app")); assertTrue(doc, doc.contains("12306"));
        Iterator<String> words = new JiebaTokenizer().call(Collections.singletonList(RowFactory.create("微信微信微信")).iterator());
        int count = 0; while (words.hasNext()) if ("微信".equals(words.next())) count++;
        assertEquals(1, count);
    }

    @Test public void sparkCsvSerializationMatchesLocalTsvForNullsEmptyStringsAndLiteralQuotes() throws Exception {
        org.apache.spark.sql.types.StructType schema = new org.apache.spark.sql.types.StructType()
                .add("id", org.apache.spark.sql.types.DataTypes.LongType)
                .add("title", org.apache.spark.sql.types.DataTypes.StringType)
                .add("value", org.apache.spark.sql.types.DataTypes.DoubleType)
                .add("empty_text", org.apache.spark.sql.types.DataTypes.StringType)
                .add("last_text", org.apache.spark.sql.types.DataTypes.StringType);
        Dataset<Row> rows = spark.createDataFrame(Arrays.asList(
                RowFactory.create(1L, "\"医保\"", null, "", null),
                RowFactory.create(2L, "\"\"", 0.0, null, " 尾部空格 ")), schema).orderBy("id");
        Path output = root.resolve("tsv-wire");
        TsvOutput.write(rows, "wire", args(root, output, "course_hot"));
        List<String> local = Files.readAllLines(output.resolve("wire/part-00000"), StandardCharsets.UTF_8);
        org.apache.spark.sql.Column[] columns = Arrays.stream(schema.fieldNames())
                .map(org.apache.spark.sql.functions::col).toArray(org.apache.spark.sql.Column[]::new);
        List<Row> encoded = rows.select(org.apache.spark.sql.functions.to_csv(
                org.apache.spark.sql.functions.struct(columns), TsvOutput.csvOptions()).as("line")).collectAsList();
        assertEquals("1\t\"医保\"\t\t\t", encoded.get(0).getString(0));
        assertEquals("2\t\"\"\t0.0\t\t 尾部空格 ", encoded.get(1).getString(0));
        assertEquals(local.get(1), encoded.get(0).getString(0));
        assertEquals(local.get(2), encoded.get(1).getString(0));
    }

    @Test public void allEightTasksShareOneFixtureAndPublishRealEvaluationStatuses() throws Exception {
        Path input = Files.createDirectories(root.resolve("batch-in"));
        Path output = root.resolve("batch-out");
        List<String> logs = new ArrayList<>();
        logs.add("user_id,course_id,study_time,progress,is_finish,study_date");
        for (int user = 1; user <= 12; user++) for (int day = 1; day <= 10; day++) {
            if (day <= 8) for (int c = 0; c < 3; c++) {
                int course = (user + c) % 8 + 1;
                logs.add(user + "," + course + "," + (user % 3 + 1) + ",40,0,2026-10-" + String.format("%02d", day));
                logs.add(user + "," + course + ",1," + (c == 0 ? "100,1" : "80,0") + ",2026-10-" + String.format("%02d", day));
            } else {
                logs.add(user + "," + ((user + 3) % 8 + 1) + ",1,100,1,2026-10-" + String.format("%02d", day));
            }
        }
        Files.write(input.resolve("study_log.csv"), logs, StandardCharsets.UTF_8);
        List<String> courses = new ArrayList<>(), train = new ArrayList<>();
        courses.add("id,title,category_name"); train.add("title,category");
        for (int c = 1; c <= 8; c++) {
            String title = c <= 4 ? "微信支付操作" + c : "手机字体设置" + c;
            String category = c <= 4 ? "支付" : "设置";
            courses.add(c + "," + title + "," + category); train.add(title + "," + category);
        }
        Files.write(input.resolve("course_info.csv"), courses, StandardCharsets.UTF_8);
        Files.write(input.resolve("course_train.csv"), train, StandardCharsets.UTF_8);
        Files.write(input.resolve("ai_question.csv"), Arrays.asList("id,user_id,question,province",
                "1,1,微信支付怎么操作,四川", "2,2,手机字体怎样调大,四川", "3,3,WiFi APP 12306,北京"), StandardCharsets.UTF_8);
        String[] tasks = {"kmeans", "course_hot", "study_trend", "fp_growth", "word_freq", "item_cf", "als_recommend", "text_classify"};
        for (String task : tasks) {
            AnalysisTask job = AnalysisTask.of(task); job.run(spark, args(input, output, task));
            for (String subdir : job.outputs()) assertTrue(subdir, Files.exists(output.resolve(subdir + "/part-00000")));
        }
        List<String> hot = Files.readAllLines(output.resolve("course_hot/part-00000"));
        for (String row : hot.subList(1, hot.size())) assertTrue(Long.parseLong(row.split("\t")[3]) <= 12);
        for (String task : Arrays.asList("item_cf", "als_recommend")) {
            String metrics = new String(Files.readAllBytes(output.resolve(task + "_metrics/part-00000")), StandardCharsets.UTF_8);
            assertTrue(metrics, metrics.contains("popular_baseline_recall_at_5"));
            assertTrue(metrics, metrics.contains("evaluated"));
            List<String> recs = Files.readAllLines(output.resolve(task + "/part-00000"));
            for (String row : recs.subList(1, recs.size())) {
                String[] fields = row.split("\t"); int user = Integer.parseInt(fields[0]), course = Integer.parseInt(fields[1]);
                for (int c = 0; c <= 3; c++) assertNotEquals((user + c) % 8 + 1, course);
            }
        }
        String textMetrics = new String(Files.readAllBytes(output.resolve("text_classify_metrics/part-00000")), StandardCharsets.UTF_8);
        assertTrue(textMetrics, textMetrics.contains("unvalidated"));
        System.out.println("REGRESSION_BATCH_OUTPUT=" + output.toAbsolutePath());
    }

    private static Args args(Path input, Path output, String task) {
        return Args.parse(new String[]{"--task", task, "--input", input.toString(), "--output", output.toString()});
    }
}
