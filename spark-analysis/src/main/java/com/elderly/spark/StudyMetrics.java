package com.elderly.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import static org.apache.spark.sql.functions.*;

/** study_time 是增量分钟；日志不是会话。所有任务共用清洗和用户课程汇总。 */
public final class StudyMetrics {
    private StudyMetrics() {}

    public static Dataset<Row> clean(Dataset<Row> logs) {
        return logs.filter(col("user_id").gt(0).and(col("course_id").gt(0))
                        .and(col("study_time").geq(0))
                        .and(col("progress").between(0, 100))
                        .and(col("is_finish").isin(0, 1))
                        .and(col("study_date").rlike("^\\d{4}-\\d{2}-\\d{2}$")))
                .withColumn("valid_date", to_date(col("study_date"), "yyyy-MM-dd"))
                .filter(col("valid_date").isNotNull())
                .drop("valid_date")
                .filter(col("study_time").gt(0).or(col("progress").gt(0)).or(col("is_finish").equalTo(1)));
    }

    public static Dataset<Row> userCourses(Dataset<Row> logs) {
        return logs.groupBy("user_id", "course_id")
                .agg(sum("study_time").as("study_time"), max("progress").as("progress"),
                        max("is_finish").as("is_finish"), min("study_date").as("first_date"));
    }

    public static Dataset<Row> userFeatures(Dataset<Row> logs) {
        Dataset<Row> daily = logs.groupBy("user_id", "study_date")
                .agg(sum("study_time").as("daily_minutes"))
                .groupBy("user_id").agg(count(lit(1)).as("study_freq"),
                        avg("daily_minutes").as("avg_study_time"));
        Dataset<Row> completion = userCourses(logs).groupBy("user_id")
                .agg(avg(when(col("is_finish").equalTo(1).or(col("progress").geq(100)), 1.0)
                        .otherwise(0.0)).as("finish_rate"));
        return daily.join(completion, "user_id");
    }
}
