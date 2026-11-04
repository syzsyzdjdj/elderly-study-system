package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.util.UploadPaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传（图片 jpg/png/gif/webp、视频 mp4，单文件 ≤100MB）
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    /** 允许的图片扩展名 */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 允许的视频扩展名 */
    private static final Set<String> VIDEO_EXTS = Set.of("mp4");

    /** 单文件大小上限 100MB */
    private static final long MAX_SIZE = 100L * 1024 * 1024;

    private final Path uploadDir;

    public UploadController(@Value("${upload.dir}") String uploadDir) {
        this.uploadDir = UploadPaths.resolve(uploadDir);
    }

    /** 上传文件，返回 {url:"/uploads/xxx"} */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("文件大小不能超过100MB");
        }
        String original = file.getOriginalFilename();
        String ext = (original == null || !original.contains("."))
                ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();

        String category;
        if (IMAGE_EXTS.contains(ext)) {
            category = "images";
        } else if (VIDEO_EXTS.contains(ext)) {
            category = "videos";
        } else {
            throw new BizException("仅支持图片(jpg/png/gif/webp)或视频(mp4)文件");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = uploadDir.resolve(category);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toFile());
        } catch (IOException e) {
            throw new BizException("文件保存失败");
        }
        Map<String, String> data = new HashMap<>();
        data.put("url", "/uploads/" + category + "/" + filename);
        return Result.success(data);
    }
}
