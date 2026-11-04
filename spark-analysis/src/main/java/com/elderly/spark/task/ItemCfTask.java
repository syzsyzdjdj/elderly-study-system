package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
import com.elderly.spark.RecommendationEvaluation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.min;
import static org.apache.spark.sql.functions.round;
import static org.apache.spark.sql.functions.row_number;
import static org.apache.spark.sql.functions.sqrt;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

/**
 * 任务6：相似课程推荐（Item-based 协同过滤）
 * "看过A的人大多也看过B" -> 课程余弦相似度 -> 每用户按已学课程聚合相似度得分，
 * 排除已学 -> Top5
 * 输出 item_cf: user_id, course_id, title, score
 */
public class ItemCfTask implements AnalysisTask {

    @Override
    public String name() {
        return "item_cf";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("item_cf", "item_cf_metrics");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        Dataset<Row> logs = Schemas.studyLog(spark, args).cache();
        Dataset<Row> courses = Schemas.courseInfo(spark, args);
        try {
            TsvOutput.write(recommend(logs, courses), "item_cf", args);
            RecommendationEvaluation.evaluate(spark, args, "item_cf", logs, courses, ItemCfTask::recommend);
        } finally { logs.unpersist(); }
    }

    public static Dataset<Row> recommend(Dataset<Row> logs, Dataset<Row> course) {
        // 1. 用户已学课程（完成或进度>=60 视为"看过"）
        Dataset<Row> watched = logs
                .filter(col("is_finish").equalTo(1).or(col("progress").geq(60)))
                .select("user_id", "course_id")
                .distinct();
        if (watched.count() == 0) {
            throw new IllegalStateException("暂无有效学习记录（需完成或进度>=60），无法生成推荐");
        }

        // 2. 课程共现计数：同一用户学过的课程两两配对
        Dataset<Row> coOccur = watched.as("a")
                .join(watched.as("b"),
                      col("a.user_id").equalTo(col("b.user_id"))
                              .and(col("a.course_id").notEqual(col("b.course_id"))))
                .groupBy(col("a.course_id").as("c1"), col("b.course_id").as("c2"))
                .agg(count(lit(1)).as("co"));

        // 3. 余弦相似度 sim = co / (sqrt(cnt(c1)) * sqrt(cnt(c2)))
        Dataset<Row> cnt = watched.groupBy("course_id").agg(count(lit(1)).as("cnt"));
        Dataset<Row> sim = coOccur
                .join(cnt.as("x"), col("c1").equalTo(col("x.course_id")))
                .join(cnt.as("y"), col("c2").equalTo(col("y.course_id")))
                .withColumn("sim", col("co").divide(sqrt(col("x.cnt").multiply(col("y.cnt")))))
                .select("c1", "c2", "sim");

        // 4. 每用户聚合相似度得分（种子=已学课程），排除已学
        Dataset<Row> scored = watched
                .join(sim, col("course_id").equalTo(col("c1")))
                .groupBy("user_id", "c2")
                .agg(sum("sim").as("score"));
        Dataset<Row> excluded = scored.as("s")
                .join(logs.select("user_id", "course_id").distinct().as("w"),
                      col("s.user_id").equalTo(col("w.user_id"))
                              .and(col("s.c2").equalTo(col("w.course_id"))),
                      "left_anti")
                .select(col("s.user_id").as("user_id"), col("s.c2").as("c2"), col("s.score"));

        // 5. 每用户 Top5
        excluded = excluded.join(course.select(col("id").as("c2")), "c2");
        WindowSpec w = Window.partitionBy("user_id").orderBy(col("score").desc(), col("c2").asc());
        Dataset<Row> top5 = excluded
                .withColumn("rn", row_number().over(w))
                .filter(col("rn").leq(5));

        // 6. 分数只表示该用户候选内的相对排序，不是概率、也不能跨用户比较。
        WindowSpec uw = Window.partitionBy("user_id");
        Dataset<Row> normalized = top5
                .withColumn("min_score", min(col("score")).over(uw))
                .withColumn("max_score", max(col("score")).over(uw))
                .withColumn("score_norm",
                        when(col("max_score").equalTo(col("min_score")), 1.0)
                                .otherwise(col("score").minus(col("min_score"))
                                        .divide(col("max_score").minus(col("min_score")))));

        // 7. 课程标题
        Dataset<Row> out = normalized.as("n")
                .join(course.as("catalog"), col("n.c2").equalTo(col("catalog.id")))
                .select(col("n.user_id").as("user_id"), col("n.c2").as("course_id"),
                        col("catalog.title").as("title"), round(col("n.score_norm"), 4).as("score"))
                .orderBy(col("user_id").asc(), col("score").desc());

        return out;
    }
}
