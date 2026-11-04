package com.elderly.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProductionEnvironmentValidatorTest {
    private StandardEnvironment production(Map<String, Object> changes) {
        var values = new HashMap<String, Object>();
        values.put("spring.profiles.active", "prod");
        values.put("spring.config.location", "classpath:/application.yml");
        values.put("DB_PASSWORD", "test-app-value");
        values.put("JWT_SECRET", "unit-test-random-key-with-at-least-32-bytes");
        values.put("DB_MIGRATION_USERNAME", "elderly_migrator");
        values.put("DB_MIGRATION_PASSWORD", "test-migration-value");
        values.put("DEEPSEEK_ENABLED", "false");
        values.putAll(changes);
        var env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("testValues", values));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        return env;
    }
    private void validate(StandardEnvironment env) { new ProductionEnvironmentValidator().postProcessEnvironment(env, null); }

    @Test void productionDoesNotImportDevelopmentCredentialsOrVmSettings() {
        var env = production(Map.of());
        assertDoesNotThrow(() -> validate(env));
        assertEquals("elderly_app", env.getProperty("spring.datasource.username"));
        assertEquals("test-app-value", env.getProperty("spring.datasource.password"));
        assertEquals("local", env.getProperty("spark.mode"));
        assertEquals("", env.getProperty("spark.yarn.host"));
        assertEquals("http", env.getProperty("chattts.mode"));
        assertEquals("/var/lib/elderly-study/uploads", env.getProperty("upload.dir"));
        assertEquals("127.0.0.1", env.getProperty("server.address"));
        assertFalse(env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class));
    }
    @Test void refusesMissingPasswordsWeakKeysRootAndSharedMigrationAccount() {
        for (var change : List.of(Map.<String, Object>of("DB_PASSWORD", ""),
                Map.<String, Object>of("JWT_SECRET", "short"), Map.<String, Object>of("DB_USERNAME", "root"),
                Map.<String, Object>of("DB_MIGRATION_USERNAME", "elderly_app"))) {
            var ex = assertThrows(IllegalStateException.class, () -> validate(production(change)));
            assertFalse(ex.getMessage().contains("test-app-value"));
        }
    }
    @Test void refusesWildcardCorsRelativeDirectoriesAndIncompleteYarnConfiguration() {
        for (var change : List.of(Map.<String, Object>of("CORS_ALLOWED_ORIGINS", "*"),
                Map.<String, Object>of("UPLOAD_DIR", "./uploads"), Map.<String, Object>of("SPARK_MODE", "yarn"),
                Map.<String, Object>of("spring.profiles.active", "prod,dev"))) {
            assertThrows(IllegalStateException.class, () -> validate(production(change)));
        }
    }
}
