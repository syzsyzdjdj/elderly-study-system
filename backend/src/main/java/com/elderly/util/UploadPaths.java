package com.elderly.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传目录解析：兼容后端从不同工作目录启动（IDEA 运行配置工作目录=项目根，
 * 命令行可能在 backend/ 下）。配置的相对路径优先在当前工作目录下查找；
 * 若当前目录不存在但父目录（项目根）下存在，则改用父目录，保证
 * 上传写入与静态资源映射始终落在同一份 uploads 目录。
 */
public final class UploadPaths {

    private UploadPaths() {
    }

    public static Path resolve(String configured) {
        Path p = Paths.get(configured);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path inCwd = Paths.get("").toAbsolutePath().resolve(p).normalize();
        if (Files.isDirectory(inCwd)) {
            return inCwd;
        }
        // 工作目录可能是 backend/，尝试上一级（项目根）
        Path viaParent = Paths.get("..").resolve(p).normalize();
        if (Files.isDirectory(viaParent)) {
            return viaParent;
        }
        // 默认用工作目录下路径（上传时会创建）
        return inCwd;
    }
}
