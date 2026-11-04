package com.elderly.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 仅允许显式指定的临时实例；普通测试不会连接开发库。 */
@EnabledIfEnvironmentVariable(named="MYSQL_INIT_TEST_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/elderly_study.*")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseInitializationMySqlTest {
    static String url;
    static String appPassword = UUID.randomUUID().toString();
    static String migrationPassword = UUID.randomUUID().toString();
    static String backupPassword = UUID.randomUUID().toString();

    static Connection root() throws Exception {
        return DriverManager.getConnection(url, "root", System.getenv().getOrDefault("MYSQL_INIT_TEST_PASSWORD", ""));
    }
    static DriverManagerDataSource application() { return new DriverManagerDataSource(url, "elderly_app", appPassword); }
    static Flyway migrations() {
        return Flyway.configure().dataSource(url, "elderly_migrator", migrationPassword)
                .locations("classpath:db/migration").baselineOnMigrate(false).load();
    }
    @BeforeAll static void initializeIsolatedInstance() throws Exception {
        url = System.getenv("MYSQL_INIT_TEST_URL");
        String expected = Objects.requireNonNull(System.getenv("MYSQL_INIT_TEST_DATADIR"), "Explicit isolated datadir required");
        try (Connection c = root(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT @@datadir, @@port")) {
                r.next();
                assertEquals(Path.of(expected).toRealPath(), Path.of(r.getString(1)).toRealPath());
                assertNotEquals(3306, r.getInt(2), "Refuse conventional development port");
            }
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='elderly_study'")) {
                r.next(); assertEquals(0, r.getInt(1), "Refuse any existing application tables");
            }
            Path template = Path.of("../deploy/mysql-users.sql.example");
            if (!Files.exists(template)) template = Path.of("deploy/mysql-users.sql.example");
            String sql = Files.readString(template).replace("__NEW_APP_PASSWORD__", appPassword)
                    .replace("__NEW_MIGRATION_PASSWORD__", migrationPassword)
                    .replace("__NEW_BACKUP_PASSWORD__", backupPassword);
            ScriptUtils.executeSqlScript(c, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        }
    }
    @Test @Order(1) void emptyDatabaseMigratesWithRestrictedAccountsAndBootstrapsAdmin() throws Exception {
        Flyway flyway = migrations();
        int pending = flyway.info().pending().length;
        assertTrue(pending > 0);
        assertEquals(pending, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        JdbcTemplate jdbc = new JdbcTemplate(application());
        assertEquals(flyway.info().current().getVersion().toString(), jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(21, jdbc.queryForObject("SELECT COUNT(*) FROM course_info", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM sys_role", Integer.class));
        for (String table : List.of("sys_user", "study_log", "study_record", "ai_question_record", "analysis_result"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);
        for (String field : List.of("font_bold", "line_height", "is_mock"))
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name=?", Integer.class, field));
        var encoder = new BCryptPasswordEncoder();
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(application()));
        String password = "Synthetic-admin-pass-2026";
        var bootstrap = new InitialAdminBootstrap(jdbc, transactions, encoder, "first_admin", password);
        bootstrap.run(null);
        String hash = jdbc.queryForObject("SELECT password FROM sys_user WHERE username='first_admin'", String.class);
        assertTrue(encoder.matches(password, hash));
        new InitialAdminBootstrap(jdbc, transactions, encoder, "other_admin", "another-test-password").run(null);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(hash, jdbc.queryForObject("SELECT password FROM sys_user WHERE username='first_admin'", String.class));
        assertThrows(Exception.class, () -> jdbc.execute("CREATE TABLE forbidden_ddl(id INT)"));
    }
    @Test @Order(2) void anExistingVersionOneBaselineSkipsV1AndPreservesActivity() throws Exception {
        // 清理仅发生在上面校验过 datadir 和非3306端口的临时实例。
        Flyway.configure().dataSource(url, "root", System.getenv().getOrDefault("MYSQL_INIT_TEST_PASSWORD", ""))
                .cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(url, "elderly_migrator", migrationPassword).target("1").load().migrate();
        try (Connection c = root(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO sys_user(id,username,password,role_id) VALUES(77,'legacy_fixture','synthetic-only',3)");
            s.executeUpdate("INSERT INTO study_log(user_id,course_id,study_time,progress,is_finish,study_date) VALUES(77,3,5,20,0,CURRENT_DATE)");
            s.execute("DROP TABLE flyway_schema_history");
        }
        Flyway flyway = migrations();
        flyway.baseline(); // 默认版本1，模拟既有项目的显式基线。
        assertEquals(flyway.info().pending().length, flyway.migrate().migrationsExecuted);
        JdbcTemplate jdbc = new JdbcTemplate(application());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=77", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM study_log WHERE user_id=77 AND study_time=5", Integer.class));
    }
}
