package com.elderly.spark;

/**
 * 命令行参数解析：--task <name> --output <dir> [--input <dir>] [--master <url>]
 *                [--jdbc-url <url> --jdbc-user <u> --jdbc-password <p>]
 * 两种取数方式：
 * - CSV 中转：--input <dir>（HDFS 或本地目录，含 study_log.csv 等三张表）
 * - JDBC 直连：--jdbc-url 等（Spark 直接读 MySQL，无需导出 CSV/上传 HDFS）
 * master 仅在本地内嵌运行时传入（local[*]）；yarn 集群提交时 spark.master 由
 * spark-submit 环境注入，绝不能覆盖。
 */
public final class Args {

    public final String task;
    /** CSV 模式的输入目录；JDBC 模式为 null */
    public final String input;
    public final String output;
    /** null 表示不设置（交给环境）；非 null 时显式 .master() */
    public final String master;
    public final int shufflePartitions;
    /** JDBC 直连 MySQL 参数；jdbcUrl 为 null 时走 CSV 模式 */
    public final String jdbcUrl;
    public final String jdbcUser;
    public final String jdbcPassword;

    private Args(String task, String input, String output, String master, int shufflePartitions,
                 String jdbcUrl, String jdbcUser, String jdbcPassword) {
        this.task = task;
        this.input = input;
        this.output = output;
        this.master = master;
        this.shufflePartitions = shufflePartitions;
        this.jdbcUrl = jdbcUrl;
        this.jdbcUser = jdbcUser;
        this.jdbcPassword = jdbcPassword;
    }

    public static Args parse(String[] args) {
        String task = null, input = null, output = null, master = null;
        String jdbcUrl = null, jdbcUser = null, jdbcPassword = null;
        int partitions = 8;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--task".equals(a) && i + 1 < args.length) {
                task = args[++i];
            } else if ("--input".equals(a) && i + 1 < args.length) {
                input = args[++i];
            } else if ("--output".equals(a) && i + 1 < args.length) {
                output = args[++i];
            } else if ("--master".equals(a) && i + 1 < args.length) {
                master = args[++i];
            } else if ("--shuffle-partitions".equals(a) && i + 1 < args.length) {
                partitions = Integer.parseInt(args[++i]);
            } else if ("--jdbc-url".equals(a) && i + 1 < args.length) {
                jdbcUrl = args[++i];
            } else if ("--jdbc-user".equals(a) && i + 1 < args.length) {
                jdbcUser = args[++i];
            } else if ("--jdbc-password".equals(a) && i + 1 < args.length) {
                jdbcPassword = args[++i];
            }
        }
        if (task == null || output == null) {
            throw new IllegalArgumentException(
                    "参数缺失，用法: --task <name> --output <dir> [--input <dir> | --jdbc-url <url> ...]");
        }
        if (input == null && jdbcUrl == null) {
            throw new IllegalArgumentException(
                    "必须提供 --input <csv目录> 或 --jdbc-url <MySQL连接串> 之一作为数据来源");
        }
        return new Args(task, input, output, master, partitions, jdbcUrl, jdbcUser, jdbcPassword);
    }
}
