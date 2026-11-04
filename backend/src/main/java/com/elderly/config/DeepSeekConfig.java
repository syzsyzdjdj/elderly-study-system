package com.elderly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek 大模型配置（application.yml 中 deepseek.* 前缀）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekConfig {

    /** API Key（可通过环境变量 DEEPSEEK_API_KEY 覆盖，避免泄露到代码仓库） */
    @lombok.ToString.Exclude
    private String apiKey;

    /** 对话接口地址 */
    private String apiUrl = "https://api.deepseek.com/chat/completions";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 是否启用大模型（关闭时智能问答仅走内置知识库） */
    private boolean enabled = true;

    /** 连接超时（毫秒） */
    private int connectTimeout = 15000;

    /** 读取超时（毫秒） */
    private int readTimeout = 60000;

    /** DeepSeek 专用 RestTemplate（带超时，避免长时间挂起） */
    @Bean
    public RestTemplate deepseekRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
