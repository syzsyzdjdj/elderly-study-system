package com.elderly.config;

import com.elderly.util.UploadPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Web MVC 配置：显式 CORS 来源、/uploads/** 静态资源映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 上传文件根目录（解析为绝对路径，兼容从不同工作目录启动） */
    private final String uploadLocation;
    private final String[] allowedOrigins;

    public WebConfig(@Value("${upload.dir}") String uploadDir,
                     @Value("${app.cors.allowed-origins:}") String origins) {
        Path dir = UploadPaths.resolve(uploadDir);
        this.uploadLocation = dir.toUri().toString(); // file:///E:/.../uploads/
        this.allowedOrigins = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).toArray(String[]::new);
    }

    /** 跨域：只允许配置的来源；同源生产部署默认关闭。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) return; // 同源 Nginx 部署无需跨域
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * /uploads/** 映射到本地磁盘上传目录。
     * 静态资源处理器原生支持 Range 请求（206 Partial Content）与 video/mp4 等 Content-Type，
     * 支持视频拖动播放。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation)
                .setCachePeriod(3600);
    }
}
