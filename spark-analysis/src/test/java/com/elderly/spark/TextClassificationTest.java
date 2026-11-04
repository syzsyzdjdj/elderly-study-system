package com.elderly.spark;

import com.elderly.spark.task.TextClassifyTask;
import org.apache.spark.ml.linalg.Vectors;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;
import org.junit.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class TextClassificationTest {
    private static SparkSession spark;
    private static Path root;
    @BeforeClass public static void start() throws Exception {
        root = Files.createTempDirectory(Paths.get("target"), "classification-test-");
        spark = SparkSession.builder().master("local[2]").appName("classification-test")
                .config("spark.ui.enabled", "false").config("spark.sql.shuffle.partitions", "2")
                .config("spark.driver.host", "127.0.0.1").config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.local.dir", root.resolve("tmp").toAbsolutePath().toString()).getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");
    }
    @AfterClass public static void stop() { if (spark != null) spark.stop(); }

    @Test public void confidenceRequiresEvidenceAndSeparationInsteadOfJustMaximumProbability() {
        assertEquals(0.9, ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(0.9, 0.1), 1.0), 1e-9);
        assertNull(ClassificationConfidence.accepted(Vectors.dense(0), Vectors.dense(0.99, 0.01), 1.0));
        assertNull(ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(1), 1.0));
        assertNull(ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(0.55, 0.45), 1.0));
        assertNull(ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(0.61, 0.39), 0.49));
        assertNull(ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(Double.NaN, 0.1), 1.0));
        assertNull(ClassificationConfidence.accepted(Vectors.dense(1), Vectors.dense(0.9, 0.9), 1.0));
        double[] interval = TextClassificationEvaluation.wilson(30, 40);
        assertEquals(0.5981, interval[0], 1e-4);
        assertEquals(0.8581, interval[1], 1e-4);
    }

    @Test public void malformedLabelFilesCannotBecomeApparentlyValidEvaluationInput() throws Exception {
        Path file = root.resolve("labels.csv");
        Files.write(file, Arrays.asList("id,question,category", "1,\"微信,支付\",支付"), StandardCharsets.UTF_8);
        assertEquals("微信,支付", LocalCsv.read(file.toString(), Schemas.QUESTION_LABEL).get(0).getString(1));
        Files.write(file, Arrays.asList("id,category,question", "1,支付,微信"), StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> LocalCsv.read(file.toString(), Schemas.QUESTION_LABEL));
        Files.write(file, Arrays.asList("id,question,category", "1,微信,支付,多余列"), StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> LocalCsv.read(file.toString(), Schemas.QUESTION_LABEL));
        Files.write(file, Arrays.asList("id,question,category", "1,\"微信,支付"), StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> LocalCsv.read(file.toString(), Schemas.QUESTION_LABEL));
    }

    @Test public void independentMetricsRejectLeakageDuplicatesConflictsStaleAndUnknownLabels() {
        List<Row> predictions = new ArrayList<>(), labels = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String gold = i % 2 == 0 ? "支付" : "设置";
            String guess = i < 30 ? gold : ClassificationConfidence.UNKNOWN;
            predictions.add(prediction(i+1, "独立提问"+i, guess, i < 30 ? 0.9 : null));
            labels.add(RowFactory.create((long)i+1, "独立提问"+i, gold));
        }
        predictions.add(prediction(101, "训练标题", "支付", 0.9));
        labels.add(RowFactory.create(101L, "训练标题", "支付"));
        predictions.add(prediction(102, "冲突提问", "支付", 0.9));
        predictions.add(prediction(103, "冲突提问", "支付", 0.9));
        labels.add(RowFactory.create(102L, "冲突提问", "支付"));
        labels.add(RowFactory.create(103L, "冲突提问", "设置"));
        // 同一规范化文本的不同原文可以有不同预测；应固定选真正带标签的最小ID。
        predictions.add(prediction(0, "独立提问0", "设置", 0.9)); // 无人工标签，不能成为代表
        predictions.add(prediction(104, "独立提问0！", "设置", 0.9));
        labels.add(RowFactory.create(104L, "独立提问0！", "支付"));
        predictions.add(prediction(105, "已修改提问", "支付", 0.9));
        labels.add(RowFactory.create(105L, "已修改提问！", "支付")); // 仅标点变化也必须重新标注
        predictions.add(prediction(106, "无效类别提问", "支付", 0.9));
        labels.add(RowFactory.create(106L, "无效类别提问", "不存在类别"));
        Map<String, Row> metrics = metricMap(TextClassificationEvaluation.evaluate(predictions(predictions),
                spark.createDataFrame(labels, Schemas.QUESTION_LABEL), training()));
        assertEquals(40, metrics.get("accuracy").getLong(2));
        assertEquals("evaluated", metrics.get("accuracy").getString(3));
        assertEquals(0.75, metrics.get("accuracy").getDouble(1), 1e-9);
        assertEquals(0.75, metrics.get("classification_coverage").getDouble(1), 1e-9);
        assertEquals(1.0, metrics.get("accepted_accuracy").getDouble(1), 1e-9);
        assertEquals(4.0/7, metrics.get("macro_f1").getDouble(1), 1e-9);
        Collections.reverse(predictions);
        Map<String, Row> reordered = metricMap(TextClassificationEvaluation.evaluate(predictions(predictions).repartition(3),
                spark.createDataFrame(labels, Schemas.QUESTION_LABEL), training()));
        assertEquals(0.75, reordered.get("accuracy").getDouble(1), 1e-9);
        assertEquals(40, reordered.get("accuracy").getLong(2));
        Map<String, Row> noLabels = metricMap(TextClassificationEvaluation.evaluate(predictions(predictions),
                spark.createDataFrame(Collections.<Row>emptyList(), Schemas.QUESTION_LABEL), training()));
        assertTrue(noLabels.get("accuracy").isNullAt(1));
        assertEquals("unvalidated", noLabels.get("accuracy").getString(3));
        Map<String, Row> small = metricMap(TextClassificationEvaluation.evaluate(predictions(predictions),
                spark.createDataFrame(labels.subList(0, 10), Schemas.QUESTION_LABEL), training()));
        assertTrue(small.get("accuracy").isNullAt(1));
        assertEquals("insufficient_data", small.get("accuracy").getString(3));
    }

    @Test public void taskPersistsConfidenceAndAbstainsOnUnseenVocabularyAndEmptyTokens() throws Exception {
        Path input = Files.createDirectories(root.resolve("input")), output = root.resolve("output");
        Files.write(input.resolve("course_train.csv"), Arrays.asList("title,category", "微信支付,支付", "微信转账,支付", "扫码付款,支付",
                "手机字体,设置", "屏幕亮度,设置", "铃声音量,设置"), StandardCharsets.UTF_8);
        Files.write(input.resolve("ai_question.csv"), Arrays.asList("id,user_id,question,province",
                "1,1,微信支付微信支付微信支付微信支付微信支付微信支付,四川", "2,1,quasarxyz nebulaxyz,四川", "3,1,!!!,四川"), StandardCharsets.UTF_8);
        Args args = Args.parse(new String[]{"--task", "text_classify", "--input", input.toString(), "--output", output.toString()});
        new TextClassifyTask().run(spark, args);
        List<String> details = Files.readAllLines(output.resolve("text_classify_detail/part-00000"), StandardCharsets.UTF_8);
        assertEquals(4, details.size());
        assertEquals("question_id\tquestion_hash\tcategory\tconfidence",details.get(0));
        for (String line : details.subList(1,details.size())) {
            String[] fields = line.split("\t",-1);
            assertTrue(fields[1].matches("[0-9a-f]{64}"));
            assertEquals("未确定业务".equals(fields[2]),fields[3].isEmpty());
        }
        List<String> rows = Files.readAllLines(output.resolve("text_classify/part-00000"), StandardCharsets.UTF_8);
        assertTrue(rows.get(0).contains("avg_confidence\tmin_confidence\thigh_confidence_count"));
        boolean accepted = false, rejected = false;
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.split("\t", -1);
            if (ClassificationConfidence.UNKNOWN.equals(fields[1])) {
                rejected = true; assertEquals("2", fields[2]); assertEquals("", fields[3]); assertEquals("", fields[4]); assertEquals("0", fields[5]);
            } else {
                accepted = true; assertEquals("支付", fields[1]); assertEquals("1", fields[2]);
                assertTrue(Double.parseDouble(fields[3]) >= 0.6 && Double.parseDouble(fields[3]) <= 1);
            }
        }
        assertTrue(accepted); assertTrue(rejected);
        String metrics = new String(Files.readAllBytes(output.resolve("text_classify_metrics/part-00000")), StandardCharsets.UTF_8);
        assertTrue(metrics.contains("unvalidated")); assertTrue(metrics.contains("mean_confidence"));
        Dataset<Row> single = spark.createDataFrame(Collections.singletonList(RowFactory.create("微信支付", "支付")), Schemas.COURSE_TRAIN);
        Dataset<Row> question = spark.createDataFrame(Collections.singletonList(RowFactory.create(1L, "四川", "微信支付")),
                new StructType().add("id", DataTypes.LongType).add("province", DataTypes.StringType).add("question", DataTypes.StringType));
        Row unknown = TextClassifyTask.predict(spark, single, question).first();
        assertEquals(ClassificationConfidence.UNKNOWN, unknown.getAs("category")); assertNull(unknown.getAs("confidence"));
    }

    private static Row prediction(long id, String text, String guess, Double confidence) {
        return RowFactory.create(id, "四川", text, guess, confidence, true);
    }

    @Test public void segmentationDoesNotMaterializeAnEntirePartitionBeforeReturningFirstRow() {
        int[] consumed = {0};
        Iterator<Row> input = new Iterator<Row>() {
            @Override public boolean hasNext() { return consumed[0] < 1000000; }
            @Override public Row next() { consumed[0]++; return RowFactory.create("微信支付", "支付"); }
        };
        Iterator<Row> output = new com.elderly.spark.wordseg.DocumentSegmenter(0).call(input);
        assertEquals(0, consumed[0]);
        assertTrue(output.hasNext()); assertEquals(0, consumed[0]);
        Row first = output.next(); assertEquals(1, consumed[0]);
        assertEquals("微信支付", first.getString(0)); assertEquals("支付", first.getString(1));
        assertTrue(first.getString(2).contains("支付"));
    }
    private static Dataset<Row> predictions(List<Row> rows) {
        return spark.createDataFrame(rows, new StructType().add("id", DataTypes.LongType).add("province", DataTypes.StringType)
                .add("question", DataTypes.StringType).add("category", DataTypes.StringType).add("confidence", DataTypes.DoubleType)
                .add("model_ready", DataTypes.BooleanType));
    }
    private static Dataset<Row> training() {
        return spark.createDataFrame(Arrays.asList(RowFactory.create("训练标题", "支付"), RowFactory.create("另一训练标题", "设置")), Schemas.COURSE_TRAIN);
    }
    private static Map<String, Row> metricMap(List<Row> rows) {
        Map<String, Row> metrics = new HashMap<>(); for (Row row : rows) metrics.put(row.getString(0), row); return metrics;
    }
}
