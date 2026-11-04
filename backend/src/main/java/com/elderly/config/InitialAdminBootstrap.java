package com.elderly.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;

/** 首次部署显式启用；数据库中已有管理员时绝不重置或覆盖密码。 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap-admin", name = "enabled", havingValue = "true")
public class InitialAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public InitialAdminBootstrap(JdbcTemplate jdbc, TransactionTemplate transaction, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.jdbc = jdbc; this.transaction = transaction; this.encoder = encoder;
        this.username = username; this.password = password;
    }
    @Override public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> {
            // 锁定角色行，防止并发首次启动创建多个管理员。
            Long role = jdbc.queryForObject("SELECT id FROM sys_role WHERE role_key='admin' FOR UPDATE", Long.class);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE role_id=?", Long.class, role) > 0) return;
            if (username == null || !username.matches("[A-Za-z0-9_]{3,50}"))
                throw new IllegalStateException("首次管理员用户名应为3-50位字母、数字或下划线");
            int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
            if (password == null || password.length() < 12 || bytes > 72)
                throw new IllegalStateException("首次管理员密码至少12个字符，且不超过72个UTF-8字节");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username=?", Long.class, username) > 0)
                throw new IllegalStateException("首次管理员用户名已被占用，请选择其他用户名");
            jdbc.update("INSERT INTO sys_user(username,password,nickname,role_id,status,create_time,update_time) "
                    + "VALUES(?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    username, encoder.encode(password), "管理员", role);
        });
    }
}
