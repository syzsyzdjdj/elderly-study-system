# YARN空指标解析及History Server记录修复（2026-10-21）

## 本次故障证据

业务快照：`c0bce5cd-cd05-4caa-b9db-1fb4fed887f6`，真实数据，104条学习日志、29条提问、21门上架课程。

`item_cf`、`als_recommend`和`text_classify`已完成集群计算和结果下载，但后端保存时出现`NumberFormatException: For input string: """"`，事务回滚后页面保留旧批次或显示未运行。

原始输出中，推荐评估文件包含如下行，其中`value`是两个字面量双引号：

```text
recall_at_5<TAB>""<TAB>2<TAB>insufficient_data<TAB>...
```

文本分类的未确定类别在`avg_confidence`、`min_confidence`列也存在相同内容。原批次的实际计算结果为Item-CF 0条、ALS 13条、文本分类9组；文本分类累计29条提问。推荐测试只有2个合格正样本，不满足至少3名已知用户、5个正样本的条件；应保留NULL指标并显示样本不足，不应判定整个任务失败。

## 修复内容

1. `TsvOutput`显式设置Spark CSV写入的`nullValue`和`emptyValue`为空字符串，禁用字段引号，并保留字符串首尾空白。集群输出与本地Java TSV输出采用相同协议。
2. `AnalysisResultStore`在可空数值字段中兼容已保存的旧YARN `""` 值。仅作用于评估`value`和分类置信度，不剥除课程标题的引号，不接受任意带引号的数字，也不把缺失值变成0。必填数值仍严格校验，错误提示包含字段名。
3. 删除`AnalysisJobMain`硬编码的`spark.eventLog.enabled=false`。YARN提交显式开启事件日志，并继续使用集群自己的事件日志目录。

集群只读检查确认：

```properties
spark.eventLog.enabled true
spark.eventLog.dir hdfs://hadoop101:9000/spark-logs
spark.history.fs.logDirectory hdfs://hadoop101:9000/spark-logs
spark.yarn.historyServer.address hadoop101:18080
```

本次不修改集群全局配置，不删除旧历史文件，不重启History Server。修复前没有事件日志的应用无法补出完整历史；修复后重新运行的应用应生成新记录。

## 验证与部署

- 本地测试包含Spark原生CSV序列化与本地TSV的逐字节文本对比，覆盖NULL、空字符串、尾列、首尾空白和含字面量引号的标题。测试不再仅依赖Java本地写文件路径。
- 后端H2事务测试复现旧YARN输出，验证Item-CF/ALS的空评估值及文本分类空置信度可以保存，评估状态及样本数保持原值。
- 本地Spark 10项测试通过；后端36项测试，35项通过、1项外部天气测试跳过。
- 分析包使用Spark 3.2 API重新编译，Java字节码为52（Java 8）。后端包已重新构建。

集群复验使用原快照的只读输入，输出写入独立验证目录：

```text
/root/spark-apps/verification/9a5d8e9b-698a-433c-b8a4-24ef5750cdce
hdfs://hadoop101:9000/user/root/spark-apps/verification/9a5d8e9b-698a-433c-b8a4-24ef5750cdce
```

验证不会把结果写入业务数据库或覆盖原快照。部署时使用更新后的后端和`spark-analysis/target/spark-analysis-1.0.0.jar`，重启仍在运行的旧后端进程，再在任务页运行失败的三项。Item-CF结果为0也应显示已执行，ALS/文本分类成功保存后应替换旧结果。无需新增数据库迁移。

2026-10-21实际YARN/Spark 3.2.0复验完成：

| 任务 | 结果行数 | History Server应用ID | 完成状态 |
|---|---:|---|---|
| item_cf | 0 | application_1792543621719_0009 | completed=true |
| als_recommend | 13 | application_1792543621719_0010 | completed=true |
| text_classify | 9 | application_1792543621719_0011 | completed=true |

复验确认两项推荐的`recall_at_5`仍为空值，样本数仍为2、状态仍为`insufficient_data`；文本分类的`accuracy`为空值、状态仍为`unvalidated`。新输出不再包含这些可空数值字段的字面量双引号。已通过History Server REST接口核对应用名、完成状态和Spark版本，证据保存在`work/spark-yarn-verification-9a5d8e9b/history-applications.json`。只读验证输入的业务快照及业务数据库均未修改。

官方说明：[Spark CSV空值选项](https://spark.apache.org/docs/3.5.6/sql-data-sources-csv.html)、[History Server与事件日志要求](https://spark.apache.org/docs/3.5.7/monitoring.html#viewing-after-the-fact)。
