package com.elderly.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import java.io.IOException;

/**
 * 输入数据读取（两种来源）：
 * - JDBC 模式（仅独立 CLI）：读取最近90天真实用户及上架课程；后端批次统一使用快照 CSV。
 *   子查询的列名/类型/空值处理与 CSV 导出（SparkCsvExporter）严格对齐。
 * - CSV 模式（args.input 目录）：本地纯 Java 读文件（避免 Windows 依赖 winutils），
 *   HDFS 路径走 spark.read()。
 */
public final class Schemas {

    /** 学习行为日志: user_id, course_id, study_time, progress, is_finish, study_date */
    public static final StructType STUDY_LOG = new StructType()
            .add("user_id", DataTypes.LongType, false)
            .add("course_id", DataTypes.LongType, false)
            .add("study_time", DataTypes.IntegerType, false)
            .add("progress", DataTypes.IntegerType, false)
            .add("is_finish", DataTypes.IntegerType, false)
            .add("study_date", DataTypes.StringType, false);

    /** 课程信息: id, title, category_name */
    public static final StructType COURSE_INFO = new StructType()
            .add("id", DataTypes.LongType, false)
            .add("title", DataTypes.StringType, false)
            .add("category_name", DataTypes.StringType, false);

    /** AI 问答记录: id, user_id, question, province（province 用于文本分类的地区维度统计） */
    public static final StructType AI_QUESTION = new StructType()
            .add("id", DataTypes.LongType, false)
            .add("user_id", DataTypes.LongType, true)
            .add("question", DataTypes.StringType, false)
            .add("province", DataTypes.StringType, true);

    /** 文本分类训练集: title, category（课程标题 + 顶级业务分类名） */
    public static final StructType COURSE_TRAIN = new StructType()
            .add("title", DataTypes.StringType, false)
            .add("category", DataTypes.StringType, false);

    public static final StructType QUESTION_LABEL = new StructType()
            .add("id", DataTypes.LongType).add("question", DataTypes.StringType).add("category", DataTypes.StringType);

    /** 老快照没有人工标签时明确输出未验证；损坏的标签文件仍按错误处理。 */
    public static Dataset<Row> questionLabels(SparkSession spark, Args args) {
        if (args.jdbcUrl != null) {
            return readJdbc(spark, args, "(SELECT q.id,q.question,l.category FROM analysis_question_label l "
                    + "JOIN ai_question_record q ON l.question_id=q.id AND BINARY l.question_text=BINARY q.question "
                    + "JOIN sys_user u ON q.user_id=u.id WHERE u.is_mock=0 AND u.status=1 "
                    + "AND q.create_time>=DATE_SUB(CURRENT_DATE, INTERVAL 89 DAY) "
                    + "AND q.create_time<DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)) t");
        }
        String file = args.input + "/question_labels.csv";
        try {
            boolean exists = args.input.startsWith("hdfs://")
                    ? new org.apache.hadoop.fs.Path(file).getFileSystem(spark.sparkContext().hadoopConfiguration())
                        .exists(new org.apache.hadoop.fs.Path(file))
                    : java.nio.file.Files.exists(java.nio.file.Paths.get(file));
            if (!exists) return spark.createDataFrame(java.util.Collections.<Row>emptyList(), QUESTION_LABEL);
            return readCsv(spark, args.input, "question_labels.csv", QUESTION_LABEL);
        } catch (IOException e) { throw new IllegalStateException("读取人工标签失败", e); }
    }

    private Schemas() {
    }

    public static Dataset<Row> studyLog(SparkSession spark, Args args) {
        if (args.jdbcUrl != null) {
            return StudyMetrics.clean(readJdbc(spark, args,
                    "(SELECT l.user_id, l.course_id, "
                            + "COALESCE(study_time, 0) AS study_time, "
                            + "COALESCE(progress, 0) AS progress, "
                            + "COALESCE(is_finish, 0) AS is_finish, "
                            + "COALESCE(DATE_FORMAT(study_date, '%Y-%m-%d'), '') AS study_date "
                            + "FROM study_log l JOIN sys_user u ON l.user_id = u.id "
                            + "JOIN course_info c ON l.course_id = c.id "
                            + "WHERE u.is_mock = 0 AND u.status = 1 AND c.status = 1 "
                            + "AND study_date BETWEEN DATE_SUB(CURRENT_DATE, INTERVAL 89 DAY) AND CURRENT_DATE) t"));
        }
        return StudyMetrics.clean(readCsv(spark, args.input, "study_log.csv", STUDY_LOG));
    }

    public static Dataset<Row> courseInfo(SparkSession spark, Args args) {
        if (args.jdbcUrl != null) {
            return readJdbc(spark, args,
                    "(SELECT c.id, COALESCE(c.title, '') AS title, "
                            + "COALESCE(cat.category_name, '') AS category_name "
                            + "FROM course_info c "
                            + "LEFT JOIN course_category cat ON c.category_id = cat.id WHERE c.status = 1) t");
        }
        return readCsv(spark, args.input, "course_info.csv", COURSE_INFO);
    }

    public static Dataset<Row> aiQuestion(SparkSession spark, Args args) {
        if (args.jdbcUrl != null) {
            return readJdbc(spark, args,
                    "(SELECT q.id, COALESCE(q.user_id, 0) AS user_id, "
                            + "COALESCE(q.question, '') AS question, "
                            + "COALESCE(u.province, '') AS province "
                            + "FROM ai_question_record q "
                            + "JOIN sys_user u ON q.user_id = u.id WHERE u.is_mock = 0 AND u.status = 1 "
                            + "AND q.create_time >= DATE_SUB(CURRENT_DATE, INTERVAL 89 DAY) "
                            + "AND q.create_time < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)) t");
        }
        return readCsv(spark, args.input, "ai_question.csv", AI_QUESTION);
    }

    /**
     * 文本分类训练集：课程标题 + 顶级业务分类名。
     * 课程可能挂在子分类上，故两次关联 course_category 取顶级分类；未分类课程 category 为空（由任务侧过滤）。
     */
    public static Dataset<Row> courseTrain(SparkSession spark, Args args) {
        if (args.jdbcUrl != null) {
            return readJdbc(spark, args,
                    "(SELECT COALESCE(c.title, '') AS title, "
                            + "COALESCE(top_cat.category_name, '') AS category "
                            + "FROM course_info c "
                            + "LEFT JOIN course_category cat ON c.category_id = cat.id "
                            + "LEFT JOIN course_category top_cat "
                            + "ON COALESCE(NULLIF(cat.parent_id, 0), c.category_id) = top_cat.id WHERE c.status = 1) t");
        }
        return readCsv(spark, args.input, "course_train.csv", COURSE_TRAIN);
    }

    /** JDBC 直读 MySQL；驱动随分析 jar 一起 shaded 分发，driver/executor 均可自动发现 */
    private static Dataset<Row> readJdbc(SparkSession spark, Args args, String dbtable) {
        return spark.read()
                .format("jdbc")
                .option("url", args.jdbcUrl)
                .option("dbtable", dbtable)
                .option("user", args.jdbcUser)
                .option("password", args.jdbcPassword)
                .option("driver", "com.mysql.cj.jdbc.Driver")
                .option("useUnicode", "true")
                .option("characterEncoding", "utf8")
                .load();
    }

    private static Dataset<Row> readCsv(SparkSession spark, String inputDir, String file, StructType schema) {
        String path = inputDir.endsWith("/") ? inputDir + file : inputDir + "/" + file;
        if (inputDir.startsWith("hdfs://")) {
            // 集群模式：HDFS 路径，走 Spark 原生读取
            return spark.read()
                    .option("header", "true")
                    .option("enforceSchema", "false")
                    .option("escape", "\"")
                    .option("mode", "FAILFAST")
                    .schema(schema)
                    .csv(path);
        }
        // 本地模式：纯 Java 读文件（避免 Windows 依赖 winutils.exe），驱动端构造 DataFrame
        try {
            return spark.createDataFrame(LocalCsv.read(path, schema), schema);
        } catch (IOException e) {
            throw new IllegalStateException("读取输入文件失败: " + path + " - " + e.getMessage(), e);
        }
    }
}
