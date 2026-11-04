package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
import com.elderly.spark.StudyMetrics;
import com.elderly.spark.RecommendationEvaluation;
import org.apache.spark.ml.recommendation.ALS;
import org.apache.spark.ml.recommendation.ALSModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.explode;
import static org.apache.spark.sql.functions.least;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.max;
import static org.apache.spark.sql.functions.round;
import static org.apache.spark.sql.functions.row_number;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.when;

/**
 * 任务7：个性化课程推荐（User-based 协同过滤 / Spark MLlib ALS 矩阵分解）
 * 纯 MySQL 难以高效完成的"用户-课程"协同过滤：把老人的学习行为构造成隐式反馈偏好强度——
 *   点击（有学习记录）= 基础分；进度 progress 越大越偏好；完成 is_finish 额外加成；
 *   停留时长 study_time 越长越偏好（封顶，避免极端值主导）。
 * ALS 隐式反馈（implicitPrefs）训练用户/课程隐向量 -> recommendForAllUsers 预测打分 ->
 * 排除已学课程 -> 每用户 Top5，关联课程标题输出。
 * 输出 als_recommend: user_id, course_id, title, score
 */
public class AlsRecommendTask implements AnalysisTask {

    /** 每用户推荐条数 */
    private static final int TOP_N = 5;
    /** 停留时长加成封顶（分钟） */
    private static final int DWELL_CAP_MIN = 60;

    @Override
    public String name() {
        return "als_recommend";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("als_recommend", "als_recommend_metrics");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        Dataset<Row> logs = Schemas.studyLog(spark, args).cache();
        Dataset<Row> courses = Schemas.courseInfo(spark, args);
        try {
            TsvOutput.write(recommend(logs, courses), "als_recommend", args);
            RecommendationEvaluation.evaluate(spark, args, "als_recommend", logs, courses, AlsRecommendTask::recommend);
        } finally { logs.unpersist(); }
    }

    public static Dataset<Row> recommend(Dataset<Row> logs, Dataset<Row> course) {

        // 1. 用户-课程维度聚合行为，构造隐式反馈偏好强度 rating（约 1.0 ~ 5.0）
        //    点击 1.0 + 进度(0~1) + 完成 1.5 + 停留时长(0~1.5，60 分钟封顶)
        Dataset<Row> ratings = StudyMetrics.userCourses(logs)
                .withColumn("rating",
                        lit(1.0)
                                .plus(least(col("progress"), lit(100)).divide(100))
                                .plus(when(col("is_finish").equalTo(1), lit(1.5)).otherwise(0))
                                .plus(least(col("study_time"), lit(DWELL_CAP_MIN))
                                        .divide(DWELL_CAP_MIN).multiply(1.5)))
                .select("user_id", "course_id", "rating")
                ;

        if (ratings.count() == 0) {
            throw new IllegalStateException("暂无学习记录，无法生成 ALS 推荐");
        }

        // 2. ALS 隐式反馈矩阵分解（implicitPrefs：rating 作为置信度/偏好强度）
        ALS als = new ALS()
                .setRank(10)
                .setMaxIter(10)
                .setRegParam(0.1)
                .setAlpha(1.0)
                .setImplicitPrefs(true)
                .setColdStartStrategy("drop")
                .setUserCol("user_id")
                .setItemCol("course_id")
                .setRatingCol("rating")
                .setSeed(42L);
        ALSModel model = als.fit(ratings);

        // 3. 为所有用户生成候选推荐并展开（recommendations: array<struct<course_id, rating>>）
        int candidateCount = Math.toIntExact(model.itemFactors().count());
        Dataset<Row> candidates = model.recommendForAllUsers(candidateCount)
                .select(col("user_id"), explode(col("recommendations")).as("rec"))
                .select(col("user_id"),
                        col("rec.course_id").as("course_id"),
                        col("rec.rating").as("score"));

        // 4. 排除已学课程（有过学习记录即不再推荐）
        Dataset<Row> learned = logs.select("user_id", "course_id").distinct();
        Dataset<Row> fresh = candidates.join(learned.as("l"),
                        candidates.col("user_id").equalTo(col("l.user_id"))
                                .and(candidates.col("course_id").equalTo(col("l.course_id"))),
                        "left_anti")
                .select(candidates.col("user_id"), candidates.col("course_id"), candidates.col("score"));

        // 5. 每用户 TopN + 关联课程标题
        fresh = fresh.join(course.select(col("id").as("course_id")), "course_id");
        WindowSpec w = Window.partitionBy("user_id").orderBy(col("score").desc(), col("course_id").asc());
        Dataset<Row> topN = fresh
                .withColumn("rn", row_number().over(w))
                .filter(col("rn").leq(TOP_N));
        Dataset<Row> out = topN.as("n")
                .join(course.as("catalog"), col("n.course_id").equalTo(col("catalog.id")))
                .select(col("n.user_id").as("user_id"),
                        col("n.course_id").as("course_id"),
                        col("catalog.title").as("title"),
                        round(col("n.score"), 4).as("score"))
                .orderBy(col("user_id").asc(), col("score").desc());

        return out;
    }
}
