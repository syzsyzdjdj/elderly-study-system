package com.elderly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * ChatTTS 语音合成服务配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chattts")
public class ChatTtsConfig {

    /** 调用模式：direct(直连本地脚本服务，无需开网页) / bridge(浏览器桥接 Gradio) / http(REST API 直连) */
    private String mode = "direct";

    /** ChatTTS HTTP 服务地址（REST API 直连模式） */
    private String apiUrl;
    /** 可选远程服务Bearer密钥，只保留在服务器环境配置中。 */
    @lombok.ToString.Exclude
    private String apiKey = "";

    /** 修改远程模型或推理参数后递增，隔离旧缓存。 */
    private String cacheVersion = "v1";
    private int connectTimeout = 5000;
    private int requestTimeout = 180000;
    private boolean startupWarmup = false;
    private boolean prewarmOnStartup = false;

    /** 桥接模式：通过本地脚本驱动 Gradio 界面生成语音（适用于未暴露 REST API 的部署） */
    private boolean bridgeEnabled = false;

    /** 桥接命令（不含 text 和输出路径两个参数，运行时自动追加） */
    private List<String> bridgeCommand;

    /** 桥接超时（毫秒），语音生成较慢，默认 5 分钟 */
    private long bridgeTimeout = 300000;

    /** 直连模式：Python 解释器路径 */
    private String directPython = "python3";

    /** 直连模式：tts_server.py 脚本路径 */
    private String directScript;
    private String appRoot = "";
    private String modelCache = "";

    /** 直连模式：本地服务地址（就绪探针 /ping，生成 /generate） */
    private String directUrl = "http://127.0.0.1:5050";

    /** 直连模式：服务端口（与 directUrl 对应） */
    private int directPort = 5050;

    /** 直连模式：服务启动等待超时（毫秒），首次启动需加载模型 */
    private long directStartupTimeout = 300000;

    /** 直连模式：单次生成请求超时（毫秒） */
    private long directRequestTimeout = 600000;

    /** RestTemplate（调用外部 TTS 服务） */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
