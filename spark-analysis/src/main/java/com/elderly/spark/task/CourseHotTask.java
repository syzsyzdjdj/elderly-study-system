package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
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
import static org.apache.spark.sql.functions.row_number;

/**
 * 任务2：课程热度排行 + 分类占比
 * 输出1 course_hot:  rank, course_id, title, study_count（按学习人次降序）
 * 输出2 course_category: category_name, study_count, share_rate
 */
public class CourseHotTask implements AnalysisTask {

    @Override
    public String name() {
        return "course_hot";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("course_hot", "course_category");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        Dataset<Row> log = Schemas.studyLog(spark, args);
        Dataset<Row> course = Schemas.courseInfo(spark, args);

        // 1. 课程热度排行
        Dataset<Row> byCourse = log
                .join(course, log.col("course_id").equalTo(course.col("id")))
                .groupBy(log.col("course_id"), course.col("title"))
                .agg(org.apache.spark.sql.functions.countDistinct("user_id").as("study_count"))
                .orderBy(col("study_count").desc());
        WindowSpec w = Window.orderBy(col("study_count").desc(), col("course_id").asc());
        Dataset<Row> ranked = byCourse
                .withColumn("rank", row_number().over(w))
                .select(col("rank"), col("course_id"), col("title"), col("study_count"));
        TsvOutput.write(ranked, "course_hot", args);

        // 2. 分类占比
        Dataset<Row> byCategory = log
                .join(course, log.col("course_id").equalTo(course.col("id")))
                .groupBy(course.col("category_name"))
                .agg(org.apache.spark.sql.functions.countDistinct("user_id").as("study_count"))
                .orderBy(col("study_count").desc());
        Row totalRow = byCategory.agg(org.apache.spark.sql.functions.sum("study_count")).head();
        long total = totalRow.isNullAt(0) ? 0L : totalRow.getLong(0);
        Dataset<Row> share = byCategory.withColumn("share_rate",
                total == 0 ? lit(0.0) : col("study_count").multiply(100.0).divide(total));
        TsvOutput.write(share, "course_category", args);
    }
}
