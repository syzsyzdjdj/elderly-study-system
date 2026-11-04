package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.util.UploadPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TTS 音频资源管理（ADMIN）：列出/上传/删除 uploads/tts 下的合成音频文件。
 */
@RestController
@RequestMapping("/api/tts/audio")
@PreAuthorize("hasRole('ADMIN')")
public class TtsAudioController {

    /** 合法文件名：32位md5或uuid，扩展名 wav/mp3（防目录穿越） */
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9_-]{8,64}\\.(wav|mp3)$");

    private final Path ttsDir;

    @PostMapping("/prepared")
    public Result<Map<String, String>> prepared(@RequestParam("text") String text,
                                               @RequestParam("file") MultipartFile file) {
        if (text == null || text.isBlank() || text.length() > 500)
            throw new BizException("请填写对应朗读分句（最多500字）");
        if (file == null || file.isEmpty() || file.getSize() > 20L * 1024 * 1024)
            throw new BizException("请选择20MB以内的音频");
        try {
            String url = com.elderly.util.PreparedTtsAudio.store(ttsDir.getParent().toString(), text, file.getBytes());
            return Result.success(Map.of("url", url, "key", com.elderly.util.PreparedTtsAudio.key(text)));
        } catch (Exception e) { throw new BizException("关联音频失败：" + e.getMessage()); }
    }

    public TtsAudioController(@Value("${upload.dir}") String uploadDir) {
        this.ttsDir = UploadPaths.resolve(uploadDir).resolve("tts");
    }

    /** 分页列出音频文件（按修改时间倒序） */
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        if (!Files.isDirectory(ttsDir)) {
            return Result.success(emptyPage(page, size));
        }
        try (Stream<Path> stream = Files.list(ttsDir)) {
            List<Path> all = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::mtime).reversed())
                    .toList();
            long total = all.size();
            List<Map<String, Object>> records = all.stream()
                    .skip(Math.max(0, (page - 1) * size))
                    .limit(size)
                    .map(this::toInfo)
                    .toList();
            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", total);
            result.put("current", page);
            result.put("size", size);
            return Result.success(result);
        } catch (Exception e) {
            throw new BizException("读取音频目录失败: " + e.getMessage());
        }
    }

    /** 上传音频素材（mp3/wav，≤20MB），文件名重命名为安全名称 */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的音频文件");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BizException("音频文件不能超过 20MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!"mp3".equals(ext) && !"wav".equals(ext)) {
            throw new BizException("仅支持 mp3 / wav 格式音频");
        }
        try {
            Files.createDirectories(ttsDir);
            // 生成符合 SAFE_NAME 的安全文件名，避免目录穿越与非法字符
            String name = "up" + UUID.randomUUID().toString().replace("-", "").substring(0, 24) + "." + ext;
            Path target = ttsDir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Map<String, Object> info = new HashMap<>();
            info.put("filename", name);
            info.put("url", "/uploads/tts/" + name);
            info.put("size", Files.size(target));
            return Result.success(info);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("上传失败: " + e.getMessage());
        }
    }

    /** 删除指定音频文件 */
    @DeleteMapping("/{filename}")
    public Result<Void> delete(@PathVariable String filename) {
        if (!SAFE_NAME.matcher(filename).matches()) {
            throw new BizException("非法文件名");
        }
        Path target = ttsDir.resolve(filename).normalize();
        if (!target.startsWith(ttsDir) || !Files.exists(target)) {
            throw new BizException("音频文件不存在");
        }
        try {
            Files.deleteIfExists(target);
        } catch (Exception e) {
            throw new BizException("删除失败: " + e.getMessage());
        }
        return Result.success();
    }

    private Map<String, Object> emptyPage(long page, long size) {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of());
        result.put("total", 0);
        result.put("current", page);
        result.put("size", size);
        return result;
    }

    private long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private Map<String, Object> toInfo(Path p) {
        Map<String, Object> info = new HashMap<>();
        String name = p.getFileName().toString();
        info.put("filename", name);
        info.put("url", "/uploads/tts/" + name);
        try {
            info.put("size", Files.size(p));
            info.put("modifiedTime", LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Files.getLastModifiedTime(p).toMillis()),
                    ZoneId.systemDefault()));
        } catch (Exception ignore) {
            info.put("size", 0);
        }
        return info;
    }
}
