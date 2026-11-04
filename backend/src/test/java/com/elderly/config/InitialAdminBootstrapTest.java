package com.elderly.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InitialAdminBootstrapTest {
    @Test void validatesPasswordAndNeverPromotesAnExistingUser() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_key VARCHAR(50))");
        jdbc.execute("INSERT INTO sys_role VALUES(1,'admin')");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(50) UNIQUE,password VARCHAR(100),nickname VARCHAR(50),role_id BIGINT,status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var encoder = new BCryptPasswordEncoder();
        assertThrows(IllegalStateException.class, () -> new InitialAdminBootstrap(jdbc,tx,encoder,"first_admin","short").run(null));
        jdbc.execute("INSERT INTO sys_user(username,password,role_id) VALUES('taken','synthetic',3)");
        assertThrows(IllegalStateException.class, () -> new InitialAdminBootstrap(jdbc,tx,encoder,"taken","long-enough-test-password").run(null));
        assertEquals(3L, jdbc.queryForObject("SELECT role_id FROM sys_user WHERE username='taken'",Long.class));
    }
}
