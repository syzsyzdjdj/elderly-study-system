package com.elderly.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Content-addressed recordings: an edited sentence can never reuse its old recording. */
public final class PreparedTtsAudio {
    private PreparedTtsAudio() {}

    public static String key(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ChatTtsUtil.cleanTextForTts(text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalArgumentException("朗读文本指纹生成失败", e); }
    }

    public static String find(String uploadDir, String text) {
        String key = key(text);
        for (String ext : List.of("wav", "mp3", "ogg")) {
            Path file = Paths.get(uploadDir, "tts", "prepared", key + "." + ext);
            try {
                if (Files.isRegularFile(file) && Files.size(file) > 12)
                    return "/uploads/tts/prepared/" + file.getFileName();
            } catch (Exception ignored) { }
        }
        return null;
    }

    public static String store(String uploadDir, String text, byte[] data) throws Exception {
        String ext = ChatTtsUtil.audioExtension(data);
        if (ext == null || data.length <= 12) throw new IllegalArgumentException("不是有效的 WAV/MP3/OGG 音频");
        Path dir = Files.createDirectories(Paths.get(uploadDir, "tts", "prepared"));
        Path target = dir.resolve(key(text) + "." + ext);
        Path tmp = Files.createTempFile(dir, "import-", ".tmp");
        try {
            Files.write(tmp, data);
            try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
        return "/uploads/tts/prepared/" + target.getFileName();
    }
}
