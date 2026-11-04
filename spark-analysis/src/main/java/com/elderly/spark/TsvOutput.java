package com.elderly.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataTypes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.regexp_replace;

/**
 * TSV 输出：\t 分隔、带表头、覆盖写。
 * - 集群模式（output 为 hdfs:// 路径）：coalesce(1) + DataFrameWriter（结果落 HDFS）
 * - 本地模式：collectAsList 到驱动端，纯 Java 写 part-00000（避免 Windows 依赖 winutils.exe）
 * 写出前把所有字符串列中的 \t \r \n 替换为空格，保证按行按 \t 解析可靠。
 */
public final class TsvOutput {

    private TsvOutput() {
    }

    /** 写 TSV 到 args.output/<subdir> */
    public static void write(Dataset<Row> df, String subdir, Args args) {
        Dataset<Row> cleaned = df;
        for (String c : df.columns()) {
            if (DataTypes.StringType.equals(df.schema().apply(c).dataType())) {
                cleaned = cleaned.withColumn(c, regexp_replace(col(c), "\\t|\\r|\\n", " "));
            }
        }
        String out = args.output.endsWith("/") ? args.output + subdir : args.output + "/" + subdir;
        if (args.output.startsWith("hdfs://")) {
            cleaned.coalesce(1).write()
                    .options(csvOptions())
                    .mode("overwrite")
                    .csv(out);
            return;
        }
        // 本地模式：合并到单分区后收集，纯 Java 写文件
        writeLocal(cleaned.coalesce(1), out);
    }

    /** 集群CSV写入与本地纯TSV保持相同空值协议；不能使用Spark默认的带引号空值。 */
    public static java.util.Map<String, String> csvOptions() {
        java.util.Map<String, String> options = new java.util.HashMap<>();
        options.put("sep", "\t");
        options.put("header", "true");
        options.put("quote", "\u0000");
        options.put("nullValue", "");
        options.put("emptyValue", "");
        options.put("ignoreLeadingWhiteSpace", "false");
        options.put("ignoreTrailingWhiteSpace", "false");
        return options;
    }

    private static void writeLocal(Dataset<Row> df, String outDir) {
        java.util.Iterator<Row> rows = df.toLocalIterator();
        String[] columns = df.columns();
        try {
            Path dir = Paths.get(outDir);
            Files.createDirectories(dir);
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve("part-00000"), StandardCharsets.UTF_8)) {
                w.write(String.join("\t", columns));
                w.write('\n');
                while (rows.hasNext()) {
                    Row r = rows.next();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < columns.length; i++) {
                        if (i > 0) {
                            sb.append('\t');
                        }
                        Object v = r.isNullAt(i) ? null : r.get(i);
                        sb.append(v == null ? "" : String.valueOf(v));
                    }
                    w.write(sb.toString());
                    w.write('\n');
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("写结果文件失败: " + outDir + " - " + e.getMessage(), e);
        }
    }
}
