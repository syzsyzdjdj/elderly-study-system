package com.elderly.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** 配置加载后、连接数据库前校验生产参数；错误中只包含配置名。 */
public class ProductionEnvironmentValidator implements EnvironmentPostProcessor, Ordered {
    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    @Override public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) return;
        if (env.acceptsProfiles(Profiles.of("dev"))) fail("prod 与 dev 不能同时启用");
        String user = required(env, "spring.datasource.username");
        if ("root".equalsIgnoreCase(user)) fail("生产数据库不能使用 root");
        required(env, "spring.datasource.password");
        if (required(env, "jwt.secret").getBytes(StandardCharsets.UTF_8).length < 32)
            fail("jwt.secret 至少需要32字节的随机密钥");
        if (env.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            String migration = required(env, "spring.flyway.user");
            if (migration.equalsIgnoreCase(user) || migration.equalsIgnoreCase("root"))
                fail("spring.flyway.user 必须是独立的非 root 迁移账号");
            required(env, "spring.flyway.password");
        }
        if (!"org.apache.ibatis.logging.nologging.NoLoggingImpl".equals(env.getProperty("mybatis-plus.configuration.log-impl")))
            fail("生产环境必须关闭 MyBatis SQL 输出");
        if (env.getProperty("app.cors.allowed-origins", "").contains("*")) fail("生产跨域来源不能使用通配符");
        for (String key : new String[]{"upload.dir", "spark.export-dir", "logging.file.name"}) {
            String value = required(env, key);
            if (!value.startsWith("/") && !Path.of(value).isAbsolute()) fail(key + " 必须使用绝对路径");
        }
        if ("yarn".equalsIgnoreCase(env.getProperty("spark.mode"))) {
            for (String key : new String[]{"spark.yarn.host", "spark.yarn.user", "spark.yarn.ssh-key", "spark.yarn.hdfs-base"}) required(env, key);
            if ("root".equalsIgnoreCase(env.getProperty("spark.yarn.user"))) fail("Spark SSH 不能使用 root");
        }
        if (env.getProperty("deepseek.enabled", Boolean.class, true)) required(env, "deepseek.api-key");
    }

    private static String required(ConfigurableEnvironment env, String key) {
        String value = env.getProperty(key, "").trim();
        if (value.isEmpty()) fail("缺少生产配置 " + key);
        return value;
    }
    private static void fail(String message) { throw new IllegalStateException(message); }
}
