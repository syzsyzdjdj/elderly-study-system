package com.elderly.service.impl;

import com.elderly.config.RecommendationConfig;
import com.elderly.config.SparkConfig;
import com.elderly.util.TsvResultParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/** 读取独立模拟快照，绝不写入或读取真实用户的模型结果表。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockRecommendationSnapshots {
    private final SparkConfig sparkConfig;
    private final RecommendationConfig recommendationConfig;

    public record Snapshot(String runId, Map<Long, List<Long>> als, Map<Long, List<Long>> itemCf) {}

    public Optional<Snapshot> latest() {
        Path runs = Paths.get(sparkConfig.getExportDir()).toAbsolutePath().normalize().resolve("runs");
        if (!Files.isDirectory(runs)) return Optional.empty();
        try (Stream<Path> paths = Files.list(runs)) {
            List<Path> directories = paths.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("[0-9a-f-]{36}"))
                    .sorted(Comparator.comparingLong(MockRecommendationSnapshots::modified).reversed()).toList();
            for (Path root : directories) {
                try {
                    Properties metadata = new Properties();
                    try (Reader r = Files.newBufferedReader(root.resolve("snapshot.properties"), StandardCharsets.UTF_8)) { metadata.load(r); }
                    if (!"mock".equals(metadata.getProperty("dataScope"))) continue;
                    LocalDate date = LocalDate.parse(metadata.getProperty("toDate", ""));
                    if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusDays(Math.max(1, recommendationConfig.getMaxModelAgeDays())))) continue;
                    // 两个任务都完整取回后才采用该批次；失败/尚在下载的批次回退到上一完整批次。
                    Map<Long, List<Long>> als = read(root, "als_recommend");
                    Map<Long, List<Long>> cf = read(root, "item_cf");
                    return Optional.of(new Snapshot(root.getFileName().toString(), als, cf));
                } catch (Exception ignored) {
                    // 扫描会遇到真实批次、未完成批次或已清理的目录；仅采用可验证的完整模拟输出。
                }
            }
        } catch (Exception e) { log.warn("无法读取模拟推荐快照目录"); }
        return Optional.empty();
    }

    private Map<Long, List<Long>> read(Path root, String task) {
        Path out = root.resolve("out");
        List<Map<String, String>> metrics = TsvResultParser.parseFile(out.resolve(task + "__" + task + "_metrics.tsv"),
                Set.of("metric", "value", "samples", "status", "detail"));
        if (metrics.isEmpty() || metrics.stream().anyMatch(m -> !Set.of("evaluated", "insufficient_data").contains(m.get("status")))) {
            throw new IllegalArgumentException("Incomplete metrics");
        }
        List<Map<String, String>> rows = TsvResultParser.parseFile(out.resolve(task + "__" + task + ".tsv"),
                Set.of("user_id", "course_id", "score", "title"));
        // 使用分数确定每个用户的名次；不把原始预测值当成概率。
        Map<Long, Map<Long, Double>> scores = new HashMap<>();
        for (Map<String, String> row : rows) {
            long user = Long.parseLong(row.get("user_id")), course = Long.parseLong(row.get("course_id"));
            double score = Double.parseDouble(row.get("score"));
            if (!Double.isFinite(score) || user <= 0 || course <= 0) throw new IllegalArgumentException("Invalid candidate");
            scores.computeIfAbsent(user, k -> new HashMap<>()).merge(course, score, Math::max);
        }
        Map<Long, List<Long>> result = new HashMap<>();
        scores.forEach((user, values) -> result.put(user, values.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList()));
        return Map.copyOf(result);
    }

    private static long modified(Path root) {
        try { return Files.getLastModifiedTime(root.resolve("snapshot.properties")).toMillis(); }
        catch (Exception e) { return Long.MIN_VALUE; }
    }
}
