package com.elderly.spark;

import com.elderly.spark.task.AlsRecommendTask;
import com.elderly.spark.task.CourseHotTask;
import com.elderly.spark.task.FpGrowthTask;
import com.elderly.spark.task.ItemCfTask;
import com.elderly.spark.task.KMeansClusterTask;
import com.elderly.spark.task.StudyTrendTask;
import com.elderly.spark.task.TextClassifyTask;
import com.elderly.spark.task.WordFreqTask;
import org.apache.spark.sql.SparkSession;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析任务注册表：任务名 -> 实现。
 * 每个任务在 output 目录下写出一个或多个 TSV 子目录，供后端解析入库。
 */
public interface AnalysisTask {

    /** 任务名（--task 参数值） */
    String name();

    /** 执行任务：按 args 取数（JDBC 直连 MySQL 或读 CSV），写 TSV 到 args.output/<各输出子目录> */
    void run(SparkSession spark, Args args);

    /** 该任务产出的输出子目录名列表（后端据此取回结果） */
    List<String> outputs();

    Map<String, AnalysisTask> REGISTRY = buildRegistry();

    static Map<String, AnalysisTask> buildRegistry() {
        Map<String, AnalysisTask> m = new LinkedHashMap<String, AnalysisTask>();
        for (AnalysisTask t : Arrays.asList(
                new KMeansClusterTask(),
                new CourseHotTask(),
                new StudyTrendTask(),
                new FpGrowthTask(),
                new WordFreqTask(),
                new ItemCfTask(),
                new AlsRecommendTask(),
                new TextClassifyTask())) {
            m.put(t.name(), t);
        }
        return m;
    }

    /** 按名字取任务，不存在则抛异常 */
    static AnalysisTask of(String name) {
        AnalysisTask t = REGISTRY.get(name);
        if (t == null) {
            throw new IllegalArgumentException("未知分析任务: " + name + "，可用: " + REGISTRY.keySet());
        }
        return t;
    }
}
