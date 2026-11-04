package com.elderly.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolConfigurationTest {
    @TempDir Path dir;
    @Test void preservesNumericAndBooleanYamlValues() throws Exception {
        Path config = dir.resolve("typed.yml");
        Files.writeString(config, "spring:\n  datasource:\n    password: 987654321\ndeepseek:\n  enabled: false\n");
        var values = ToolConfiguration.load(config);
        assertEquals("987654321", values.getProperty("spring.datasource.password"));
        assertEquals("false", values.getProperty("deepseek.enabled"));
    }
    @Test void readsPrivateOverridesAndResolvesPlaceholdersWithoutStartingApplication() throws Exception {
        Path config = dir.resolve("private.yml");
        Files.writeString(config, "spring:\n  datasource:\n    password: ${tool.test.password}\n");
        System.setProperty("tool.test.password", "synthetic-test-value");
        try {
            var values = ToolConfiguration.load(config);
            assertEquals("synthetic-test-value", values.getProperty("spring.datasource.password"));
            assertFalse(values.getProperty("spark.mode").contains("${"));
        } finally { System.clearProperty("tool.test.password"); }
    }
}
