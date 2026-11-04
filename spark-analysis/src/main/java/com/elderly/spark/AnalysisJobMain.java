package com.elderly.spark;

import org.apache.spark.sql.SparkSession;

/**
 * 分析任务入口。
 * - 集群模式：spark-submit --class com.elderly.spark.AnalysisJobMain --task xxx ...
 *   spark.master 由提交环境注入，不调 .master()
 * - 本地模式：后端 JVM 内直接调 run(Args)，--master local[*]
 * 可重入：每次 run 结束 spark.stop()，无 System.exit。
 */
public final class AnalysisJobMain {

    private AnalysisJobMain() {
    }

    public static void main(String[] args) {
        run(Args.parse(args));
    }

    /** 可重入执行入口（后端本地模式直接调用） */
    public static void run(Args args) {
        SparkSession.Builder b = SparkSession.builder()
                .appName("elderly-analysis-" + args.task)
                .config("spark.ui.enabled", "false")
                .config("spark.sql.legacy.timeParserPolicy", "CORRECTED")
                .config("spark.sql.shuffle.partitions", String.valueOf(args.shufflePartitions));
        if (args.master != null && !args.master.isEmpty()) {
            b = b.master(args.master);
            if (args.master.equals("local") || args.master.startsWith("local[")) {
                b = b.config("spark.driver.bindAddress", "127.0.0.1")
                        .config("spark.driver.host", "127.0.0.1");
            }
        }
        SparkSession spark = b.getOrCreate();
        try {
            AnalysisTask.of(args.task).run(spark, args);
        } finally {
            spark.stop();
        }
    }
}
