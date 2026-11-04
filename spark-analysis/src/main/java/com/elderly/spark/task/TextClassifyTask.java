package com.elderly.spark.task;

import com.elderly.spark.*;
import com.elderly.spark.wordseg.DocumentSegmenter;
import org.apache.spark.ml.classification.NaiveBayes;
import org.apache.spark.ml.feature.*;
import org.apache.spark.ml.linalg.Vector;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.types.*;
import java.util.*;
import static org.apache.spark.sql.functions.*;

/** 课程标题弱监督分类；人工提问标签只做独立测试，不进入词表、IDF或分类器训练。 */
public class TextClassifyTask implements AnalysisTask {
    @Override public String name() { return "text_classify"; }
    @Override public List<String> outputs() { return Arrays.asList("text_classify", "text_classify_metrics", "text_classify_detail"); }

    @Override public void run(SparkSession spark, Args args) {
        Dataset<Row> train = Schemas.courseTrain(spark, args)
                .filter(col("category").isNotNull().and(length(trim(col("category"))).gt(0))
                        .and(col("title").isNotNull()).and(length(trim(col("title"))).gt(0)))
                .withColumn("category", trim(col("category")));
        Dataset<Row> questions = Schemas.aiQuestion(spark, args)
                .select(col("id"), when(length(trim(coalesce(col("province"), lit("")))).equalTo(0), lit("未知地区"))
                        .otherwise(trim(col("province"))).as("province"), coalesce(col("question"), lit("")).as("question"))
                .filter(length(trim(col("question"))).gt(0));
        Dataset<Row> labeled = predict(spark, train, questions).cache();
        try {
            TsvOutput.write(labeled.select(col("id").as("question_id"), sha2(col("question"), 256).as("question_hash"),
                    col("category"), col("confidence")), "text_classify_detail", args);
            Dataset<Row> stats = labeled.groupBy("province", "category")
                    .agg(count(lit(1)).as("question_count"), avg("confidence").as("avg_confidence"),
                            min("confidence").as("min_confidence"),
                            sum(when(col("confidence").geq(0.8), 1L).otherwise(0L)).as("high_confidence_count"))
                    .orderBy(col("province"), col("question_count").desc())
                    .select("province", "category", "question_count", "avg_confidence", "min_confidence", "high_confidence_count");
            TsvOutput.write(stats, "text_classify", args);
            long questionCount = labeled.count();
            long unknown = labeled.filter(col("category").equalTo(ClassificationConfidence.UNKNOWN)).count();
            List<Row> metrics = new ArrayList<>();
            metrics.add(RowFactory.create("undetermined_rate", questionCount == 0 ? null : (double) unknown / questionCount, questionCount,
                    questionCount == 0 ? "insufficient_data" : "descriptive",
                    "空特征/单类别/词汇覆盖<0.5/概率<0.6/类别差距<0.15归为未确定；模型概率不等于正确率"));
            Row confidence = labeled.agg(avg("confidence"), count("confidence")).first();
            metrics.add(RowFactory.create("mean_confidence", confidence.isNullAt(0) ? null : confidence.getDouble(0),
                    confidence.getLong(1), "descriptive", "仅统计已接受分类的最大类别概率；未经概率校准，不代表准确率"));
            metrics.addAll(TextClassificationEvaluation.evaluate(labeled, Schemas.questionLabels(spark, args), train));
            ModelMetrics.writeRows(spark, args, name(), metrics);
        } finally { labeled.unpersist(); }
    }

    /** 输入问题列序为id/province/question；输出每条提问的最终类别及可用置信度。 */
    public static Dataset<Row> predict(SparkSession spark, Dataset<Row> train, Dataset<Row> questions) {
        // 标题去重，互相冲突的弱标签不用于训练。
        Dataset<Row> uniqueTrain = train.withColumn("text_key", TextClassificationEvaluation.key(col("title")))
                .filter(length(col("text_key")).gt(0))
                .groupBy("text_key").agg(min("title").as("title"), min("category").as("category"),
                        countDistinct("category").as("categories"))
                .filter(col("categories").equalTo(1)).select("title", "category");
        StructType trainSchema = new StructType().add("title", DataTypes.StringType)
                .add("category", DataTypes.StringType).add("seg", DataTypes.StringType);
        Dataset<Row> trainWords = spark.createDataFrame(uniqueTrain.javaRDD().mapPartitions(new DocumentSegmenter(0)), trainSchema)
                .filter(length(col("seg")).gt(0)).withColumn("words", split(col("seg"), " "));
        if (trainWords.select("category").distinct().count() < 2) return unknown(questions);

        StringIndexerModel indexer = new StringIndexer().setInputCol("category").setOutputCol("label").fit(trainWords);
        // 显式训练词表，未见词不会哈希碰撞到已知业务词；词表只由训练标题生成。
        CountVectorizerModel vectorizer = new CountVectorizer().setInputCol("words").setOutputCol("rawFeatures")
                .setVocabSize(20000).setMinDF(1.0).fit(trainWords);
        Dataset<Row> trainTf = vectorizer.transform(indexer.transform(trainWords));
        IDFModel idf = new IDF().setInputCol("rawFeatures").setOutputCol("features").fit(trainTf);
        org.apache.spark.ml.classification.NaiveBayesModel model = new NaiveBayes().setSmoothing(1.0)
                .setModelType("multinomial").setFeaturesCol("features").setLabelCol("label").fit(idf.transform(trainTf));

        StructType questionSchema = new StructType().add("id", DataTypes.LongType).add("province", DataTypes.StringType)
                .add("question", DataTypes.StringType).add("seg", DataTypes.StringType);
        Dataset<Row> questionWords = spark.createDataFrame(questions.select("id", "province", "question").javaRDD()
                .mapPartitions(new DocumentSegmenter(2)), questionSchema)
                .withColumn("words", expr("filter(split(seg, ' '), x -> length(x) > 0)"));
        Set<String> vocabulary = new HashSet<>(Arrays.asList(vectorizer.vocabulary()));
        UDF1<String, Double> coverage = seg -> {
            if (seg == null || seg.isEmpty()) return 0.0;
            String[] words = seg.split(" "); long known = 0;
            for (String word : words) if (vocabulary.contains(word)) known++;
            return (double) known / words.length;
        };
        Dataset<Row> predictions = model.transform(idf.transform(vectorizer.transform(questionWords)))
                .withColumn("vocabulary_coverage", udf(coverage, DataTypes.DoubleType).apply(col("seg")));
        predictions = new IndexToString().setInputCol("prediction").setOutputCol("candidate_category")
                .setLabels(indexer.labelsArray()[0]).transform(predictions);
        UDF3<Vector, Vector, Double, Double> confidence = ClassificationConfidence::accepted;
        return predictions.withColumn("confidence", udf(confidence, DataTypes.DoubleType)
                        .apply(col("features"), col("probability"), col("vocabulary_coverage")))
                .withColumn("category", when(col("confidence").isNotNull(), col("candidate_category"))
                        .otherwise(lit(ClassificationConfidence.UNKNOWN)))
                .withColumn("model_ready", lit(true))
                .select("id", "province", "question", "category", "confidence", "model_ready");
    }

    private static Dataset<Row> unknown(Dataset<Row> questions) {
        return questions.withColumn("category", lit(ClassificationConfidence.UNKNOWN))
                .withColumn("confidence", lit(null).cast(DataTypes.DoubleType)).withColumn("model_ready", lit(false))
                .select("id", "province", "question", "category", "confidence", "model_ready");
    }
}
