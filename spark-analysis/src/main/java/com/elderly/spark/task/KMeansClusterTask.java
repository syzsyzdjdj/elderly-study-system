package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
import com.elderly.spark.StudyMetrics;
import org.apache.spark.ml.evaluation.ClusteringEvaluator;
import com.elderly.spark.ModelMetrics;
import org.apache.spark.ml.clustering.KMeans;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.StandardScaler;
import org.apache.spark.ml.feature.StandardScalerModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.types.DataTypes;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.avg;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.desc;
import static org.apache.spark.sql.functions.udf;

/**
 * 任务1：学习人群聚类（K-Means，k=2）
 * 特征：窗口内去重活跃天数、每个活跃日累计分钟、用户课程完成率。
 * 输出相对活跃组和轮廓系数，不推断连续学习/停学复学等未测量行为。
 */
public class KMeansClusterTask implements AnalysisTask {

    @Override
    public String name() {
        return "kmeans";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("kmeans", "kmeans_metrics");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        // 1. 按用户聚合特征
        Dataset<Row> userFeature = StudyMetrics.userFeatures(Schemas.studyLog(spark, args));
        long n = userFeature.count();
        if (n < 3) {
            throw new IllegalStateException("参与分析的用户数不足3人（当前 " + n + " 人），无法进行 K=2 聚类");
        }
        if (userFeature.select("study_freq", "avg_study_time", "finish_rate").distinct().count() < 2) {
            throw new IllegalStateException("用户特征完全相同，不应强行划分两个学习群体");
        }

        // 2. 向量化
        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(new String[]{"study_freq", "avg_study_time", "finish_rate"})
                .setOutputCol("features");
        Dataset<Row> assembled = assembler.transform(userFeature);

        // 3. 标准化（消除量纲影响）
        StandardScaler scaler = new StandardScaler()
                .setInputCol("features")
                .setOutputCol("scaledFeatures")
                .setWithMean(true)
                .setWithStd(true);
        StandardScalerModel scalerModel = scaler.fit(assembled);
        Dataset<Row> scaled = scalerModel.transform(assembled);

        // 4. K-Means(k=2)
        KMeans kmeans = new KMeans()
                .setK(2)
                .setSeed(42L)
                .setFeaturesCol("scaledFeatures")
                .setPredictionCol("prediction");
        KMeansModel model = kmeans.fit(scaled);
        Dataset<Row> predictions = model.transform(scaled);
        long clusters = predictions.select("prediction").distinct().count();
        double silhouette = clusters > 1 ? new ClusteringEvaluator().setFeaturesCol("scaledFeatures")
                .setPredictionCol("prediction").evaluate(predictions) : Double.NaN;
        ModelMetrics.write(spark, args, "kmeans", "silhouette", silhouette, n,
                clusters > 1 ? "evaluated" : "unvalidated", "标准化特征平方欧氏距离；不是业务有效性证明");

        // 5. 各簇综合得分（标准化特征之和）与原始特征均值，按得分降序
        UDF1<Vector, Double> vecSumUdf = v -> {
            double sum = 0;
            for (double d : v.toArray()) {
                sum += d;
            }
            return sum;
        };
        Dataset<Row> stats = predictions
                .withColumn("score", udf(vecSumUdf, DataTypes.DoubleType).apply(col("scaledFeatures")))
                .groupBy("prediction")
                .agg(avg("score").as("score_avg"),
                     count(col("user_id")).as("user_count"),
                     avg("avg_study_time").as("avg_study_time"),
                     avg("finish_rate").as("avg_finish_rate"))
                .orderBy(desc("score_avg"))
                .select(col("prediction").as("cluster_id"),
                        col("score_avg"), col("user_count"),
                        col("avg_study_time"), col("avg_finish_rate"));

        TsvOutput.write(stats, "kmeans", args);
    }
}
