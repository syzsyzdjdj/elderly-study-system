package com.elderly.util;

import com.elderly.common.BizException;
import com.elderly.config.SparkConfig;
import com.elderly.spark.AnalysisJobMain;
import com.elderly.spark.Args;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 分析任务统一入口：
 * 导出不可变快照 CSV，按配置在本地或 YARN 执行，统一解析 TSV；批次内快照复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRunner {

    private final SparkConfig sparkConfig;
    private final SparkCsvExporter csvExporter;
    private final YarnSubmitUtil yarnSubmitUtil;
    private Path batchInput;

    /** 一次导出、同一数据库快照，覆盖整批分析；锁覆盖计算及调用方落库。 */
    public synchronized <T> T withSnapshot(Supplier<T> action) {
        if (batchInput != null) throw new BizException("已有批次正在执行");
        batchInput = csvExporter.export();
        try {
            return action.get();
        } finally {
            batchInput = null;
        }
    }

    public synchronized String snapshotId() {
        return batchInput == null ? "" : batchInput.getParent().getFileName().toString();
    }

    /**
     * 运行指定分析任务
     * 合法任务名称：kmeans / course_hot / study_trend / fp_growth / word_freq / item_cf / als_recommend
     */
    public synchronized Map<String, List<Map<String, String>>> runTask(String taskName) {
        // 校验任务名字符串（和spark‑analysis模块AnalysisTask内部枚举名字保持一致）
        List<String> validTaskList = List.of(
                "kmeans",
                "course_hot",
                "study_trend",
                "fp_growth",
                "word_freq",
                "item_cf",
                "als_recommend",
                "text_classify"
        );
        if (!validTaskList.contains(taskName)) {
            throw new BizException("非法任务名称：" + taskName);
        }

        try {
            // 后端统一走不可变快照，避免各任务 JDBC 取到不同时间的数据。
            Path input = batchInput == null ? csvExporter.export() : batchInput;
            return runAnalysis(taskName, input);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("分析任务 {} 执行失败", taskName, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new BizException("分析任务[" + taskName + "]执行失败：" + msg);
        }
    }

    /**
     * 本地内嵌或 SSH 提交 YARN；两条路径使用相同的输出表头校验。
     */
    private Map<String, List<Map<String, String>>> runAnalysis(String taskName, Path input) {
        // 传给YarnSubmitUtil的输出名称列表，必须和spark-analysis模块各AnalysisTask的outputs()/TsvOutput.write子目录名保持一致
        List<String> outputNames = switch (taskName) {
            case "kmeans" -> List.of("kmeans", "kmeans_metrics");
            case "course_hot" -> List.of("course_hot", "course_category");
            case "study_trend" -> List.of("study_trend");
            case "fp_growth" -> List.of("fp_growth");
            case "word_freq" -> List.of("word_freq");
            case "item_cf" -> List.of("item_cf", "item_cf_metrics");
            case "als_recommend" -> List.of("als_recommend", "als_recommend_metrics");
            case "text_classify" -> List.of("text_classify", "text_classify_metrics", "text_classify_detail");
            default -> throw new BizException("未配置该任务输出列表:" + taskName);
        };

        Map<String, Path> files;
        if ("local".equalsIgnoreCase(sparkConfig.getMode())) {
            String master = sparkConfig.getMasterUrl();
            if (master == null || !master.matches("local(?:\\[(?:[1-9][0-9]*|\\*)\\])?")) {
                throw new BizException("本地分析必须配置 local 或 local[n] 类型的 Spark master");
            }
            Path output = input.toAbsolutePath().getParent().resolve("out").resolve(taskName);
            AnalysisJobMain.run(Args.parse(new String[]{
                    "--task", taskName, "--input", input.toAbsolutePath().toString(),
                    "--output", output.toString(), "--master", master,
                    "--shuffle-partitions", "2"
            }));
            files = new LinkedHashMap<>();
            for (String name : outputNames) files.put(name, output.resolve(name).resolve("part-00000"));
        } else if ("yarn".equalsIgnoreCase(sparkConfig.getMode())) {
            files = yarnSubmitUtil.submitSnapshot(taskName, outputNames, input);
        } else {
            throw new BizException("不支持的 Spark 执行模式：" + sparkConfig.getMode());
        }
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Path> e : files.entrySet()) {
            result.put(e.getKey(), TsvResultParser.parseFile(e.getValue(), expectedColumns(e.getKey())));
        }
        return result;
    }

    private java.util.Set<String> expectedColumns(String output) {
        if (output.endsWith("_metrics")) return java.util.Set.of("metric", "value", "samples", "status", "detail");
        return switch (output) {
            case "kmeans" -> java.util.Set.of("cluster_id", "score_avg", "user_count", "avg_study_time", "avg_finish_rate");
            case "course_hot" -> java.util.Set.of("rank", "course_id", "title", "study_count");
            case "course_category" -> java.util.Set.of("category_name", "study_count", "share_rate");
            case "study_trend" -> java.util.Set.of("study_date", "study_count", "study_duration_min");
            case "fp_growth" -> java.util.Set.of("antecedent_ids", "consequent_ids", "confidence", "support", "lift");
            case "word_freq" -> java.util.Set.of("rank", "word", "freq");
            case "item_cf", "als_recommend" -> java.util.Set.of("user_id", "course_id", "title", "score");
            case "text_classify" -> java.util.Set.of("province", "category", "question_count", "avg_confidence", "min_confidence", "high_confidence_count");
            case "text_classify_detail" -> java.util.Set.of("question_id", "question_hash", "category", "confidence");
            default -> throw new BizException("未知输出: " + output);
        };
    }

}
