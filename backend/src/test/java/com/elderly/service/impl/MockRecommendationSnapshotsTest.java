package com.elderly.service.impl;

import com.elderly.config.RecommendationConfig;
import com.elderly.config.SparkConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MockRecommendationSnapshotsTest {
    @TempDir Path root;
    private MockRecommendationSnapshots store() {
        SparkConfig config = new SparkConfig(); config.setExportDir(root.toString());
        return new MockRecommendationSnapshots(config, new RecommendationConfig());
    }
    @Test void onlyCompleteMockSnapshotIsUsedWithExactIdsAndStableDeduplicatedRanks() throws Exception {
        Path valid = snapshot("mock", LocalDate.now(), true, 1);
        Files.writeString(valid.resolve("out/als_recommend__als_recommend.tsv"),
                "user_id\tcourse_id\ttitle\tscore\n9007199254740993\t5\t五\t0.1\n9007199254740993\t3\t三\t0.8\n9007199254740993\t5\t五\t0.8\n");
        snapshot("real", LocalDate.now(), true, 2);
        var result = store().latest().orElseThrow();
        assertEquals(valid.getFileName().toString(), result.runId());
        assertEquals(List.of(3L, 5L), result.als().get(9007199254740993L));
    }
    @Test void partialMalformedExpiredAndFutureSnapshotsFallBackToLastUsableBatch() throws Exception {
        Path valid = snapshot("mock", LocalDate.now(), true, 1);
        snapshot("mock", LocalDate.now(), false, 2);
        Path malformed = snapshot("mock", LocalDate.now(), true, 3);
        Files.writeString(malformed.resolve("out/item_cf__item_cf.tsv"), "user_id\tcourse_id\ttitle\tscore\n6\t3\t三\tNaN\n");
        snapshot("mock", LocalDate.now().minusDays(31), true, 4);
        snapshot("mock", LocalDate.now().plusDays(1), true, 5);
        assertEquals(valid.getFileName().toString(), store().latest().orElseThrow().runId());
    }
    @Test void missingDirectoryOrNoEligibleBatchIsAnEmptyResult() throws Exception {
        assertTrue(store().latest().isEmpty());
        snapshot("real", LocalDate.now(), true, 1);
        assertTrue(store().latest().isEmpty());
    }
    private Path snapshot(String scope, LocalDate date, boolean complete, int order) throws Exception {
        Path path = Files.createDirectories(root.resolve("runs/" + UUID.randomUUID()));
        Properties properties = new Properties(); properties.setProperty("dataScope", scope); properties.setProperty("toDate", date.toString());
        try (var w = Files.newBufferedWriter(path.resolve("snapshot.properties"))) { properties.store(w, "test"); }
        Files.setLastModifiedTime(path.resolve("snapshot.properties"), FileTime.fromMillis(order * 1000L));
        Path out = Files.createDirectories(path.resolve("out"));
        for (String task : complete ? List.of("als_recommend", "item_cf") : List.of("als_recommend")) {
            Files.writeString(out.resolve(task + "__" + task + ".tsv"), "user_id\tcourse_id\ttitle\tscore\n6\t3\t三\t1.0\n");
            Files.writeString(out.resolve(task + "__" + task + "_metrics.tsv"), "metric\tvalue\tsamples\tstatus\tdetail\nrecall_at_5\t1.0\t30\tevaluated\ttest\n");
        }
        return path;
    }
}
