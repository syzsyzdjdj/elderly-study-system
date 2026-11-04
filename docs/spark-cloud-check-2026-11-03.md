# 云端 Spark 执行检查

> 开源副本已脱敏：服务器地址、实例标识和账号均为示例，不能用于访问原部署。

检查日期：2026-11-03。目标为已部署的广州 CVM `example-instance` / `203.0.113.10`，2核2GiB。

最新结论：同日已修复并上线本地执行分支。8项线上任务中7项完成计算、解析和落库；K-Means由于参与分析的真实用户仅1人，被最低3人的业务校验阻止。下面保留修复前检查记录，不能将任务目录加载成功或 HTTP 200 当作执行成功。

## 修复与上线验收

- `TaskRunner` 按 `spark.mode` 分流本地或YARN。本地直接调用已内嵌的分析模块，复用同一批次快照、TSV表头校验和事务落库，任务继续串行执行。
- 本地 master 限制为 `local` / `local[n]` / `local[*]`；Spark driver绑定127.0.0.1，UI关闭。服务器沿用 `local[1]`、640MiB堆、128MiB直接内存和256MiB元空间，无需独立集群或新增公网端口。
- 管理页移除了固定提交YARN的描述。Web构建通过；后端12项相关测试通过，覆盖真实Spark热度计算、连续重建Spark上下文、快照复用、YARN路由、模式校验和结果存储。
- 通过OrcaTerm上传7,369,538字节增量包，SHA-256为 `9e2740fb958437a109357ac9c68d48f29a7eacac264b14491cbd6fd6ed0cb355`。先校验旧包指纹并保留 `/opt/elderly-study/backend.before-spark-local.jar`，替换业务类及分析模块后重启一次后端。新JAR SHA-256：`973dada4c897f0db794426db4d9a1ba5c41e14f53505f8245dc59cee4e563757`。

线上逐项调用 `/api/analysis/run/{task}`，检查 `data.success`，再用SQL核对 `analysis_model_metric` 的快照完成记录：

| 任务 | 结果 | 耗时（秒） |
| --- | --- | ---: |
| course_hot | 完成并落库 | 16 |
| study_trend | 完成并落库 | 1 |
| kmeans | 真实学习用户1人，最低要求3人；未生成伪造聚类 | — |
| fp_growth | 完成并落库 | 2 |
| word_freq | 完成并落库 | 3 |
| item_cf | 完成并落库；recall_at_5为insufficient_data | 5 |
| als_recommend | 完成并落库；recall_at_5为insufficient_data | 14 |
| text_classify | 完成并落库；accuracy为unvalidated | 8 |

原始返回保存在服务器 `/home/ubuntu/spark-local-verification.json`；日志 `/home/ubuntu/spark-local-verification.log`。未向生产库注入模拟用户以凑齐训练样本。任务完成不代表推荐质量或分类准确率已达标，K-Means还要求用户特征不能完全相同。

公网HTTPS首页、下载页、下载元数据和课程API均返回200。后台、MySQL、Nginx运行状态正常。此验收为当前小规模数据的顺序执行，未验证大规模训练或高并发能力。

## 实测

通过服务器现有管理员账号在本机回环 API 完成认证，未输出密码或 token。任务目录接口返回业务码200；随后只运行 `course_hot` 单项任务，未执行全部任务、生成模拟数据或修改运行配置。

任务返回：

```json
{
  "code": 200,
  "data": {
    "task": "course_hot",
    "runId": "183576da-d844-4323-8783-b096423cc542",
    "success": false,
    "message": "找不到分析 jar: /opt/elderly-study/spark-analysis/target/spark-analysis-1.0.0.jar，请先执行 mvn -f spark-analysis/pom.xml clean install"
  }
}
```

现场配置为 `SPARK_MODE=local`、`SPARK_MASTER=local[1]`、`SPARK_EXPORT_DIR=/var/lib/elderly-study/spark-tmp`。可用内存约1120MiB，已配置2GiB交换空间；未发现 `spark-submit`、`hadoop` 或 `yarn` 命令。这次失败发生在Spark计算启动之前，不能据此判断算法性能或这台机器的并发承载能力。

## 原因

部署包只包含网站后端 JAR，没有按 `spark.yarn.jar-path` 默认路径部署独立分析 JAR。当前 `TaskRunner.runTask()` 不判断 `spark.mode`，而是直接调用 `runOnYarn()`，继而由 `YarnSubmitUtil` 查找独立分析 JAR、通过 SSH 提交至YARN。已构建的部署类也没有本地执行方法，因此不只是文档或当前源码显示差异。

`SparkConfig` 和部署文档仍描述本地内嵌模式，和实际提交代码不一致。仅补传分析JAR仍不足以完成任务，后面还缺少目标YARN集群、SSH及HDFS配置。

## 后续处理方向

若继续使用这台小服务器，应先恢复并验证 `local[1]` 执行分支，复用已有快照与结果校验流程，限制任务并发，并从小规模数据开始验证内存及网站可用性。另一条路径是配置独立YARN集群，由网站负责提交；不建议为了本次小范围试用直接在2GiB业务机上叠加Hadoop/YARN服务。

本次仅检查与记录，没有擅自切换架构、安装集群或重启后端。失败任务未产生成功分析结果；网站和数据库仍可用。
