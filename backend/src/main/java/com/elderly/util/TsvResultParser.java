package com.elderly.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Spark 任务 TSV 结果解析：跳表头，按列名映射，返回 List<Map<String,String>>。
 * 兼容两种来源：本地模式 part-* 目录 / YARN 模式 getmerge 合并后的单文件。
 */
public final class TsvResultParser {

    private TsvResultParser() {
    }

    /** 解析单个 TSV 文件 */
    public static List<Map<String, String>> parseFile(Path file) {
        return parseFile(file, java.util.Collections.emptySet());
    }

    public static List<Map<String, String>> parseFile(Path file, java.util.Set<String> requiredColumns) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取分析结果失败: " + file + " - " + e.getMessage(), e);
        }
        if (lines.isEmpty()) {
            throw new IllegalStateException("分析结果文件缺少表头: " + file);
        }
        List<String> header = Arrays.asList(lines.get(0).split("\t"));
        if (!header.containsAll(requiredColumns) || new java.util.HashSet<>(header).size() != header.size()) {
            throw new IllegalStateException("分析结果表头不符: " + file + "，必须包含 " + requiredColumns);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            List<String> cols = new ArrayList<>(Arrays.asList(line.split("\t", -1)));
            // 输出协议是禁用 quote 的纯 TSV，不能剥掉标题本身的引号。
            if (cols.size() != header.size()) {
                throw new IllegalStateException("分析结果列数不符(期望 " + header.size()
                        + " 实际 " + cols.size() + "): " + line);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < header.size(); j++) {
                row.put(header.get(j), cols.get(j));
            }
            rows.add(row);
        }
        return rows;
    }

    /** 解析本地模式下某输出子目录里的 part-* 文件（coalesce(1) 保证只有一个） */
    public static List<Map<String, String>> parseDir(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            Path part = stream.filter(p -> p.getFileName().toString().startsWith("part-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("分析结果目录为空: " + dir));
            return parseFile(part);
        } catch (IOException e) {
            throw new IllegalStateException("读取分析结果目录失败: " + dir + " - " + e.getMessage(), e);
        }
    }

}
