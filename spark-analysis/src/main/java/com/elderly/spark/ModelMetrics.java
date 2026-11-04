package com.elderly.spark;

import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import java.util.Collections;
import java.util.List;

public final class ModelMetrics {
    private ModelMetrics() {}
    public static void write(SparkSession spark, Args args, String task, String metric,
                             double value, long samples, String status, String detail) {
        Row row = RowFactory.create(metric, Double.isFinite(value) ? value : null, samples, status, detail);
        writeRows(spark, args, task, Collections.singletonList(row));
    }

    public static void writeRows(SparkSession spark, Args args, String task, List<Row> rows) {
        StructType schema = new StructType().add("metric", DataTypes.StringType)
                .add("value", DataTypes.DoubleType).add("samples", DataTypes.LongType)
                .add("status", DataTypes.StringType).add("detail", DataTypes.StringType);
        TsvOutput.write(spark.createDataFrame(rows, schema), task + "_metrics", args);
    }
}
