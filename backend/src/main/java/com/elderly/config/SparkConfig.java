package com.elderly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Spark 配置
 * mode=local：后端 JVM 内嵌运行（master-url 一般 local[*]，无需安装 Spark）
 * mode=yarn ：通过 SSH 把分析任务 spark-submit 到自己的 YARN 集群（yarn.* 配置）
 */
@Data
@Component
@ConfigurationProperties(prefix = "spark")
public class SparkConfig {

    /** 运行模式：local（内嵌）| yarn（SSH 提交集群） */
    private String mode = "local";

    /** Spark 运行模式（本地模式用），默认 local[*] */
    private String masterUrl = "local[2]";

    /** 后端本地临时目录：导出 CSV 与取回 TSV 结果 */
    private String exportDir = "./spark-tmp";

    /** real（默认）或 mock；禁止混用真实与模拟数据。 */
    private String dataScope = "real";

    /** 所有任务共用同一快照中的最近 N 天数据。 */
    private int lookbackDays = 90;

    /** YARN 集群提交配置 */
    private SparkYarnConfig yarn = new SparkYarnConfig();

    @Data
    public static class SparkYarnConfig {

        /** VM 主机名或 IP */
        private String host = "";

        /** SSH 用户名 */
        private String user = "spark";

        /** SSH 私钥路径（Windows 路径） */
        private String sshKey = "";

        /** VM 上工作目录（jar 与 CSV/TSV 中转） */
        private String remoteDir = "/opt/elderly-spark";

        /** HDFS 基础路径（输入与输出都挂在其下） */
        private String hdfsBase = "";

        /** 本地 spark-analysis jar 路径（相对路径基于后端或项目根工作目录均可，resolveJar 会自动尝试） */
        private String jarPath = "spark-analysis/target/spark-analysis-1.0.0.jar";

        /** spark-submit 命令名（含 PATH 则可用绝对路径） */
        private String sparkSubmitCmd = "spark-submit";

        /** driver/executor 内存（单机伪分布式 VM 建议 1g） */
        private String driverMemory = "1g";
        private String executorMemory = "1g";
        private int numExecutors = 1;
        private int executorCores = 1;

        /** spark-submit 阻塞等待超时（毫秒） */
        private long submitTimeout = 600000;

        /**
         * JDBC 直连 MySQL 取数（Spark 任务直接读 MySQL，跳过 CSV 导出/SCP/HDFS put）。
         * 配置后走 JDBC 模式；留空则走 CSV 中转老链路。
         * 注意：URL 中的主机必须是 VM 能访问到的 Windows 地址（如 VMware NAT 网关 192.168.240.1）。
         */
        private String jdbcUrl;
        private String jdbcUser;
        private String jdbcPassword;
    }
}
