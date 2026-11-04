package com.elderly.util;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import java.nio.file.Path;
import java.util.Properties;

/** 独立工具也解析环境变量；不再把 ${DB_PASSWORD:} 当成字面密码。 */
public final class ToolConfiguration {
    private ToolConfiguration() {}
    public static Properties load() {
        String custom = System.getenv("TOOLS_CONFIG_FILE");
        return load(custom == null || custom.isBlank() ? Path.of("backend/config/application-local.yml") : Path.of(custom));
    }
    public static Properties load(Path privateConfig) {
        Properties combined = new Properties();
        merge(combined, new ClassPathResource("application.yml"));
        merge(combined, new FileSystemResource(privateConfig));
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addLast(new PropertiesPropertySource("toolConfig", combined));
        Properties resolved = new Properties();
        // YAML 数字/布尔值也须转换为字符串，stringPropertyNames 会跳过它们。
        for (Object name : combined.keySet()) {
            String key = name.toString();
            resolved.setProperty(key, env.getProperty(key, ""));
        }
        return resolved;
    }
    private static void merge(Properties target, Resource resource) {
        if (!resource.exists()) return;
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(resource);
        Properties values = yaml.getObject();
        if (values != null) target.putAll(values);
    }
}
