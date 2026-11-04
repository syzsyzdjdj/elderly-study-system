package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
import org.apache.spark.ml.fpm.FPGrowth;
import org.apache.spark.ml.fpm.FPGrowthModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.collect_list;
import static org.apache.spark.sql.functions.concat_ws;
import static org.apache.spark.sql.functions.desc;
import static org.apache.spark.sql.functions.size;

/**
 * 任务4：课程关联规则挖掘（FP-Growth 频繁项集）
 * 每用户学过的课程集合作为购物篮 -> FPGrowth -> 关联规则
 * 输出 fp_growth: antecedent_ids, consequent_ids, confidence, support（id 逗号串，标题由后端映射）
 */
public class FpGrowthTask implements AnalysisTask {

    @Override
    public String name() {
        return "fp_growth";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("fp_growth");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        // 每用户课程购物篮（去重）
        Dataset<Row> baskets = Schemas.studyLog(spark, args)
                .select("user_id", "course_id")
                .distinct()
                .groupBy("user_id")
                .agg(collect_list(col("course_id").cast(DataTypes.StringType)).as("items"));

        FPGrowth fp = new FPGrowth()
                .setItemsCol("items")
                .setMinSupport(0.01)
                .setMinConfidence(0.5);
        FPGrowthModel model = fp.fit(baskets);

        Dataset<Row> rules = model.associationRules()
                // 只保留单前件规则（A -> B），便于展示"学了A的人也学了B"
                .filter(size(col("antecedent")).equalTo(1))
                // 排除仅由热门后件造成的高置信度；同时要求至少 5 个用户共同学习。
                .filter(col("lift").gt(1.05).and(col("support").multiply(baskets.count()).geq(5)))
                .select(concat_ws(",", col("antecedent")).as("antecedent_ids"),
                        concat_ws(",", col("consequent")).as("consequent_ids"),
                        col("confidence"), col("support"), col("lift"))
                .orderBy(desc("lift"), desc("support"), desc("confidence"), col("antecedent_ids"))
                .limit(30);

        TsvOutput.write(rules, "fp_growth", args);
    }
}
