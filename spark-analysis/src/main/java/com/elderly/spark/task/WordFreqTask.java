package com.elderly.spark.task;

import com.elderly.spark.AnalysisTask;
import com.elderly.spark.Args;
import com.elderly.spark.Schemas;
import com.elderly.spark.TsvOutput;
import com.elderly.spark.wordseg.JiebaTokenizer;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.desc;
import static org.apache.spark.sql.functions.row_number;

/**
 * 任务5：AI 问答热点词频统计
 * 对 ai_question_record 的提问做 jieba 分词 -> 过滤 -> 计数 -> Top20
 * 输出 word_freq: rank, word, freq
 */
public class WordFreqTask implements AnalysisTask {

    @Override
    public String name() {
        return "word_freq";
    }

    @Override
    public List<String> outputs() {
        return Arrays.asList("word_freq");
    }

    @Override
    public void run(SparkSession spark, Args args) {
        Dataset<Row> questions = Schemas.aiQuestion(spark, args)
                .select(col("question").as("value"));
        long n = questions.count();
        if (n == 0) {
            throw new IllegalStateException("暂无 AI 问答记录，请先使用智能问答或生成模拟数据");
        }

        Dataset<String> words = questions.mapPartitions(new JiebaTokenizer(), Encoders.STRING());
        Dataset<Row> freq = words
                .groupBy("value")
                .agg(count("*").as("freq"))
                .filter(col("freq").geq(3))
                .orderBy(desc("freq"), col("value").asc())
                .limit(20);
        WindowSpec w = Window.orderBy(col("freq").desc(), col("value").asc());
        Dataset<Row> ranked = freq
                .withColumn("rank", row_number().over(w))
                .select(col("rank"), col("value").as("word"), col("freq"));
        TsvOutput.write(ranked, "word_freq", args);
    }
}
