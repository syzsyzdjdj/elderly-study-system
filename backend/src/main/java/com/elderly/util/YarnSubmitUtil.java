package com.elderly.util;

import com.elderly.common.BizException;
import com.elderly.config.SparkConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 通过 SSH 把分析任务 spark-submit 到自己的 YARN 集群（单机伪分布式 VM）。
 * 流程：scp jar+CSV -> 远程脚本(hdfs put + spark-submit 阻塞至完成 + 结果合并) -> scp 取回 TSV。
 * VM 不需要连笔记本 MySQL，只依赖笔记本->VM 一条 SSH 通道。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YarnSubmitUtil {

    private final SparkConfig sparkConfig;

    private static final ExecutorService READER_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ssh-reader");
        t.setDaemon(true);
        return t;
    });

    /**
     * 提交任务并取回结果。
     * @return outputName -> 本地合并后的 TSV 文件
     */
    public Map<String, Path> submit(String task, List<String> outputs) {
        return submit(task, outputs, null);
    }

    public Map<String, Path> submitSnapshot(String task, List<String> outputs, Path input) {
        return submit(task, outputs, input);
    }

    private Map<String, Path> submit(String task, List<String> outputs, Path snapshot) {
        SparkConfig.SparkYarnConfig y = sparkConfig.getYarn();
        Path jar = resolveJar(y);
        boolean jdbcMode = snapshot == null && y.getJdbcUrl() != null && !y.getJdbcUrl().isBlank();
        String suffix = snapshot == null ? "" : "/runs/" + snapshot.getParent().getFileName();
        if (!suffix.isEmpty() && !suffix.matches("/runs/[0-9a-f-]{36}")) {
            throw new BizException("非法分析快照目录");
        }
        String remoteDir = y.getRemoteDir() + suffix;
        String hdfsBase = y.getHdfsBase() + suffix;
        Path inDir = snapshot == null ? Paths.get(sparkConfig.getExportDir(), "in") : snapshot;
        Path outDir = snapshot == null ? Paths.get(sparkConfig.getExportDir(), "out") : snapshot.getParent().resolve("out");
        try {
            Files.createDirectories(outDir);
        } catch (IOException e) {
            throw new BizException("创建结果目录失败: " + e.getMessage());
        }

        // 1. 确保远程目录存在
        log.info("[YARN] 确保远程工作目录存在");
        ssh("mkdir -p " + remoteDir + "/in " + remoteDir + "/out", 30_000);

        // 2. 上传 jar；JDBC 模式无需上传输入 CSV（Spark 直连 MySQL 读数据）
        log.info("[YARN] 上传分析 jar 到 {}@{}（取数模式: {}）", y.getUser(), y.getHost(),
                jdbcMode ? "JDBC 直连 MySQL" : "CSV 中转");
        scp(jar, y.getUser() + "@" + y.getHost() + ":" + remoteDir + "/spark-analysis.jar", 120_000);
        if (!jdbcMode) {
            for (String f : Arrays.asList("study_log.csv", "course_info.csv", "ai_question.csv", "course_train.csv", "question_labels.csv")) {
                Path local = inDir.resolve(f);
                if (!Files.exists(local)) {
                    throw new BizException("缺少导出文件 " + local + "，请先导出分析数据");
                }
                scp(local, y.getUser() + "@" + y.getHost() + ":" + remoteDir + "/in/" + f, 120_000);
            }
        }

        // 2. 远程执行：hdfs put -> spark-submit（阻塞至完成）-> 各输出合并
        String remoteScript = buildRemoteScript(task, outputs, jdbcMode, remoteDir, hdfsBase);
        log.info("[YARN] spark-submit 提交任务 {} 到 YARN（超时 {} 秒）", task, y.getSubmitTimeout() / 1000);
        String sshOut = ssh(remoteScript, y.getSubmitTimeout());
        log.info("[YARN] 任务 {} 提交完成\n{}", task, head(sshOut, 400));

        // 3. 取回各输出 TSV
        Map<String, Path> result = new LinkedHashMap<>();
        for (String out : outputs) {
            Path local = outDir.resolve(task + "__" + out + ".tsv");
            scp(y.getUser() + "@" + y.getHost() + ":" + remoteDir + "/out/" + task + "__" + out + ".tsv",
                    local, 120_000);
            result.put(out, local);
        }
        return result;
    }

    /**
     * 解析 jarPath：依次尝试 工作目录相对路径、父目录相对路径（兼容工作目录为 backend/ 或项目根两种启动方式）
     */
    private Path resolveJar(SparkConfig.SparkYarnConfig y) {
        Path configured = Paths.get(y.getJarPath());
        if (configured.isAbsolute()) {
            if (Files.exists(configured)) {
                return configured;
            }
        } else {
            Path inWorkdir = Paths.get("").toAbsolutePath().resolve(configured).normalize();
            if (Files.exists(inWorkdir)) {
                return inWorkdir;
            }
            Path viaParent = Paths.get("..").resolve(configured).normalize();
            if (Files.exists(viaParent)) {
                return viaParent;
            }
        }
        throw new BizException("找不到分析 jar: " + configured.toAbsolutePath()
                + "，请先执行 mvn -f spark-analysis/pom.xml clean install");
    }

    /** 远程脚本：set -e 原子失败；hdfs 命令用去 scheme 的路径 */
    String buildRemoteScript(String task, List<String> outputs, boolean jdbcMode, String remoteDir, String hdfsBase) {
        SparkConfig.SparkYarnConfig y = sparkConfig.getYarn();
        String hdfsPath = hdfsBase.substring(hdfsBase.indexOf("/", "hdfs://".length()));
        if (!remoteDir.matches("/[A-Za-z0-9_./-]+") || remoteDir.contains("..") || remoteDir.equals("/")
                || !hdfsPath.matches("/[A-Za-z0-9_./-]+") || hdfsPath.contains("..") || hdfsPath.equals("/")) {
            throw new BizException("分析目录必须为安全的绝对路径，不能包含上级目录或 shell 字符");
        }
        StringBuilder sb = new StringBuilder("set -e; A=").append(remoteDir).append("; ")
                .append("mkdir -p $A/in $A/out; ");
        if (jdbcMode) {
            // JDBC 模式：Spark 直连 MySQL 读输入，无需上传 CSV/HDFS put，只清输出目录
            sb.append("hdfs dfs -rm -r -f ").append(hdfsPath).append("/out/").append(task).append("; ");
        } else {
            // CSV 模式：重建输入目录并上传 CSV
            sb.append("hdfs dfs -mkdir -p ").append(hdfsPath).append("/in; ")
              .append("hdfs dfs -put -f $A/in/* ").append(hdfsPath).append("/in/; ");
        }
        sb.append(y.getSparkSubmitCmd())
                .append(" --master yarn --deploy-mode cluster")
                .append(" --conf spark.yarn.submit.waitAppCompletion=true")
                .append(" --conf spark.eventLog.enabled=true")
                .append(" --class com.elderly.spark.AnalysisJobMain")
                .append(" --driver-memory ").append(y.getDriverMemory())
                .append(" --executor-memory ").append(y.getExecutorMemory())
                .append(" --num-executors ").append(y.getNumExecutors())
                .append(" --executor-cores ").append(y.getExecutorCores())
                .append(" $A/spark-analysis.jar --task ").append(task)
                .append(" --output ").append(hdfsBase).append("/out/").append(task);
        if (jdbcMode) {
            // 用单引号包裹：JDBC URL 含 & 等特殊字符，Windows ssh.exe 传输远程命令时会剥离双引号，
            // 导致 & 被远程 bash 当作后台运算符截断命令；单引号是普通字节不会被 ssh 动，
            // 且本类 ssh() 的 replace("'","'\\''") 会正确转义内部单引号。
            sb.append(" --jdbc-url '").append(y.getJdbcUrl()).append("'")
              .append(" --jdbc-user '").append(y.getJdbcUser()).append("'")
              .append(" --jdbc-password '").append(y.getJdbcPassword()).append("'");
        } else {
            sb.append(" --input ").append(hdfsBase).append("/in");
        }
        sb.append("; ");
        for (String out : outputs) {
            sb.append("hdfs dfs -cat ").append(hdfsPath).append("/out/").append(task)
              .append("/").append(out).append("/part-* > $A/out/").append(task)
              .append("__").append(out).append(".tsv; ");
        }
        return sb.toString();
    }

    // ---------- 命令执行（ProcessBuilder + reader 线程 + 超时强杀，仿 ChatTtsUtil 模式） ----------

    private String ssh(String remoteScript, long timeoutMs) {
        SparkConfig.SparkYarnConfig y = sparkConfig.getYarn();
        List<String> cmd = new ArrayList<>();
        cmd.add("ssh");
        addCommonOptions(cmd, y);
        cmd.add(y.getUser() + "@" + y.getHost());
        cmd.add("bash");
        cmd.add("-lc");
        // Windows ProcessBuilder 不会自动给含空格的参数加引号，
        // 手动用单引号包裹，确保整个脚本作为单个参数传给 bash -c
        cmd.add("'" + remoteScript.replace("'", "'\\''") + "'");
        return exec(cmd, timeoutMs, "SSH 远程执行失败");
    }

    private void scp(Path local, String remote, long timeoutMs) {
        scpCmd(local.toString(), remote, timeoutMs);
    }

    private void scp(String remote, Path local, long timeoutMs) {
        scpCmd(remote, local.toString(), timeoutMs);
    }

    private void scpCmd(String from, String to, long timeoutMs) {
        SparkConfig.SparkYarnConfig y = sparkConfig.getYarn();
        List<String> cmd = new ArrayList<>();
        cmd.add("scp");
        addCommonOptions(cmd, y);
        cmd.add(from);
        cmd.add(to);
        exec(cmd, timeoutMs, "SCP 传输失败: " + from + " -> " + to);
    }

    private void addCommonOptions(List<String> cmd, SparkConfig.SparkYarnConfig y) {
        cmd.add("-i");
        cmd.add(y.getSshKey());
        cmd.add("-o");
        cmd.add("StrictHostKeyChecking=accept-new");
        cmd.add("-o");
        cmd.add("BatchMode=yes");
        cmd.add("-o");
        cmd.add("ConnectTimeout=15");
    }

    private String exec(List<String> cmd, long timeoutMs, String errPrefix) {
        log.debug("执行: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new BizException(errPrefix + "（无法启动 " + cmd.get(0)
                    + "，请确认已安装 OpenSSH 客户端）: " + e.getMessage());
        }
        StringBuilder out = new StringBuilder();
        Future<?> reader = READER_POOL.submit(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            } catch (IOException ignored) {
                // 进程结束时流关闭
            }
            return null;
        });
        boolean finished;
        try {
            finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new BizException(errPrefix + "（线程被中断）");
        }
        if (!finished) {
            p.destroyForcibly();
            throw new BizException(errPrefix + "（超时 " + timeoutMs / 1000 + " 秒，已强制终止）。"
                    + "最后输出:\n" + head(out.toString(), 400));
        }
        try {
            reader.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // 输出收集线程一般已随进程退出
        }
        int code = p.exitValue();
        if (code != 0) {
            throw new BizException(errPrefix + "（退出码 " + code + "）。输出:\n" + head(out.toString(), 400));
        }
        return out.toString();
    }

    private String head(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\n...（共 " + s.length() + " 字符，已截断）";
    }
}
