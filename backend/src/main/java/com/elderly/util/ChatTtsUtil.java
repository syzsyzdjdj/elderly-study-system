package com.elderly.util;

import com.elderly.common.BizException;
import com.elderly.config.ChatTtsConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ChatTTS 语音合成工具：
 * 请求体 {text, speed}；返回 JSON 时透传 audio_url，返回音频字节流时保存为本地 wav 并返回 /uploads/tts/xxx.wav
 */
@Slf4j
@Component
public class ChatTtsUtil {

    private final RestTemplate restTemplate;
    private final ChatTtsConfig chatTtsConfig;
    private final String uploadDir;

    /** 直连模式服务启动锁：防止并发请求同时拉起服务 */
    private final Object directStartLock = new Object();

    public ChatTtsUtil(RestTemplate restTemplate,
                       ChatTtsConfig chatTtsConfig,
                       @Value("${upload.dir}") String uploadDir) {
        this.restTemplate = restTemplate;
        this.chatTtsConfig = chatTtsConfig;
        this.uploadDir = uploadDir;
    }

    /** 方言/在线音色 -> edge-tts 声音名（微软在线语音，需联网） */
    private static final Map<String, String> EDGE_VOICES = Map.ofEntries(
            // 方言
            Map.entry("edge:dongbei", "zh-CN-liaoning-XiaobeiNeural"),
            Map.entry("edge:shaanxi", "zh-CN-shaanxi-XiaoniNeural"),
            // 普通话·在线（女声）
            Map.entry("edge:xx-female", "zh-CN-XiaoxiaoNeural"),
            Map.entry("edge:xy-female", "zh-CN-XiaoyiNeural"),
            Map.entry("edge:xc-female", "zh-CN-XiaochenNeural"),
            Map.entry("edge:xh-female", "zh-CN-XiaohanNeural"),
            Map.entry("edge:xq-female", "zh-CN-XiaoqiuNeural"),
            Map.entry("edge:xr-female", "zh-CN-XiaoruiNeural"),
            Map.entry("edge:xm-female", "zh-CN-XiaomengNeural"),
            // 普通话·在线（男声）
            Map.entry("edge:yj-male", "zh-CN-YunjianNeural"),
            Map.entry("edge:yx-male", "zh-CN-YunxiNeural"),
            Map.entry("edge:yxiao-male", "zh-CN-YunxiaNeural"),
            Map.entry("edge:yy-male", "zh-CN-YunyangNeural"),
            Map.entry("edge:yf-male", "zh-CN-YunfengNeural"),
            Map.entry("edge:yz-male", "zh-CN-YunzeNeural"),
            Map.entry("edge:yh-male", "zh-CN-YunhaoNeural"),
            // 粤语
            Map.entry("edge:cantonese", "zh-HK-HiuGaaiNeural"),
            Map.entry("edge:cantonese-female2", "zh-HK-HiuMaanNeural"),
            Map.entry("edge:cantonese-male", "zh-HK-WanLungNeural"),
            // 台湾话
            Map.entry("edge:tw-female", "zh-TW-HsiaoChenNeural"),
            Map.entry("edge:tw-female2", "zh-TW-HsiaoYuNeural"),
            Map.entry("edge:tw-male", "zh-TW-YunJheNeural"));

    /**
     * 生成语音
     *
     * @param text   文本
     * @param speed  语速(可为空，直连/桥接模式由前端播放器倍速控制)
     * @param voice  音色(可为空：default/elderly/.../edge:dongbei 等，见 /api/tts/voices)
     * @param rate   语速百分比(可为空，如 "-15%"，仅 edge-tts 模式生效)
     * @param volume 音量百分比(可为空，如 "+50%"，仅 edge-tts 模式生效)
     * @param pitch  音调(可为空，如 "+10Hz"，仅 edge-tts 模式生效)
     * @return 音频地址（远程 audio_url 或本地 /uploads/tts/xxx.wav）
     */
    public String generate(String text, Double speed, String voice, String rate, String volume, String pitch) {
        // 清洗文本：去除 Markdown 标记（**、#、` 等），避免 TTS 朗读"星号"或合成异常
        text = cleanTextForTts(text);
        if (text == null || text.isBlank()) {
            throw new BizException("语音合成文本为空");
        }
        // A shared recording represents the default voice, never a requested external voice.
        boolean defaultVoice = voice == null || voice.isBlank() || "default".equals(voice);
        if ("system".equalsIgnoreCase(chatTtsConfig.getMode()) && !defaultVoice) {
            throw new BizException(systemModeReason(voice));
        }
        if (defaultVoice) {
            String prepared = PreparedTtsAudio.find(uploadDir, text);
            if (prepared != null) return prepared;
        }
        if ("system".equalsIgnoreCase(chatTtsConfig.getMode())) {
            throw new BizException("当前服务器仅提供已有录音，未启用语音合成服务；这段文字没有对应录音，因此无法生成语音。可尝试设备系统朗读，或联系管理员配置合成服务。");
        }
        // 方言音色走 edge-tts 在线合成（1~3 秒，需联网）
        if (voice != null && voice.startsWith("edge:")) {
            return generateViaEdge(text, voice, rate, volume, pitch);
        }
        // direct：直连本地 tts_server.py 服务，无需打开 ChatTTS 网页界面
        if ("bridge".equalsIgnoreCase(chatTtsConfig.getMode())) {
            return generateViaBridge(text);
        }
        if ("http".equalsIgnoreCase(chatTtsConfig.getMode())) {
            return generateViaHttp(text, speed, voice, rate, volume, pitch);
        }
        return generateViaDirect(text, voice);
    }

    public static String systemModeReason(String voice) {
        String engine = voice != null && voice.startsWith("edge:") ? "在线 TTS" : "ChatTTS";
        return "当前服务器使用已有录音与设备朗读模式，未启用" + engine
                + "合成服务，因此无法生成所选音色。可尝试设备默认朗读，或联系管理员配置语音合成服务。";
    }

    /**
     * edge-tts 方言生成：命令行调用微软在线语音（不需要本地 ChatTTS 服务）。
     * 相同文字+音色+参数按 MD5 缓存 mp3，重复收听秒回。
     *
     * @param rate   语速百分比，如 "-15%"；null/空则不传
     * @param volume 音量百分比，如 "+50%"；null/空则不传
     * @param pitch  音调，如 "+10Hz"；null/空则不传
     */
    private String generateViaEdge(String text, String voice, String rate, String volume, String pitch) {
        String edgeVoice = EDGE_VOICES.get(voice);
        if (edgeVoice == null) {
            edgeVoice = EDGE_VOICES.get("edge:dongbei");
        }
        // 缓存 key 纳入 rate/volume/pitch，参数变更后自动重新生成
        String cacheKey = text + "|" + voice + "|" + (rate == null ? "" : rate)
                + "|" + (volume == null ? "" : volume) + "|" + (pitch == null ? "" : pitch);
        String filename = md5(cacheKey) + ".mp3";
        Path dir = Paths.get(uploadDir, "tts");
        Path out = dir.resolve(filename);
        // 缓存命中：直接返回已有音频
        try {
            if (Files.exists(out) && Files.size(out) > 0) {
                log.info("TTS 缓存命中({}): {}", voice, text.substring(0, Math.min(20, text.length())));
                return "/uploads/tts/" + filename;
            }
        } catch (Exception ignore) {
        }
        log.info("TTS 方言生成开始({} rate={} vol={} pitch={}): {}", voice, rate, volume, pitch,
                text.substring(0, Math.min(20, text.length())));
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(filename + ".tmp.mp3");

            List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                    chatTtsConfig.getDirectPython(), "-m", "edge_tts",
                    "--voice", edgeVoice,
                    "--text", text,
                    "--write-media", tmp.toAbsolutePath().toString()));
            // 注意：负值参数（如 -15%、-10Hz）必须用等号写法，空格写法会被 argparse 误判为选项（exit 2）
            if (rate != null && !rate.isBlank()) {
                cmd.add("--rate=" + rate);
            }
            if (volume != null && !volume.isBlank()) {
                cmd.add("--volume=" + volume);
            }
            if (pitch != null && !pitch.isBlank()) {
                cmd.add("--pitch=" + pitch);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 并行读输出，防止管道写满阻塞子进程
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            java.util.concurrent.Future<String> outputFuture = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            });
            executor.shutdown();

            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException("方言语音生成超时，请检查网络");
            }
            String output = outputFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            int code = process.exitValue();
            if (code != 0 || !Files.exists(tmp) || Files.size(tmp) == 0) {
                log.warn("edge-tts 生成失败(code={}): {}", code, output.trim());
                throw new BizException("方言语音生成失败，请检查网络");
            }
            Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("TTS 方言生成完成: {} ({}KB)", filename, Files.size(out) / 1024);
            return "/uploads/tts/" + filename;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("edge-tts 调用异常: {}", e.getMessage());
            throw new BizException("方言语音服务不可用（需联网）");
        }
    }

    /**
     * 桥接模式：调用本地脚本（无头浏览器自动操作 Gradio 界面），生成后保存到 uploads/tts
     * 相同文字只生成一次（按文本 MD5 缓存），重复收听秒回
     */
    private String generateViaBridge(String text) {
        String filename = md5(text) + ".wav";
        Path dir = Paths.get(uploadDir, "tts");
        Path out = dir.resolve(filename);
        // 缓存命中：直接返回已有音频
        try {
            if (Files.exists(out) && Files.size(out) > 0) {
                log.info("TTS 缓存命中: {}", text.substring(0, Math.min(20, text.length())));
                return "/uploads/tts/" + filename;
            }
        } catch (Exception ignore) {
        }
        log.info("TTS 桥接生成开始: {}", text.substring(0, Math.min(20, text.length())));
        try {
            Files.createDirectories(dir);

            List<String> cmd = new java.util.ArrayList<>(chatTtsConfig.getBridgeCommand());
            cmd.add(text);
            cmd.add(out.toAbsolutePath().toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 并行读输出，防止管道写满阻塞子进程
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            java.util.concurrent.Future<String> outputFuture = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            });
            executor.shutdown();

            boolean finished = process.waitFor(chatTtsConfig.getBridgeTimeout(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException("语音生成超时，请稍后再试");
            }
            String output = outputFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
            int code = process.exitValue();
            if (code != 0) {
                log.warn("TTS 桥接脚本执行失败(code={}): {}", code, output);
                throw new BizException("语音服务暂不可用");
            }
            if (!Files.exists(out) || Files.size(out) == 0) {
                throw new BizException("语音服务返回为空");
            }
            log.info("TTS 桥接生成完成: {} ({}KB)", filename, Files.size(out) / 1024);
            return "/uploads/tts/" + filename;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TTS 桥接执行异常: {}", e.getMessage());
            throw new BizException("语音服务暂不可用");
        }
    }

    /**
     * 直连模式：POST 到本地 tts_server.py 服务（无需打开网页/浏览器）。
     * 服务未启动时自动拉起（首次启动需加载模型，可能要等 1~2 分钟），
     * 相同文字+音色只生成一次（按 MD5 缓存），重复收听秒回。
     */
    /** 直连生成缓存版本：与 tts_server.py 的 PARAMS_VERSION 保持同步，
     *  TTS 参数调优后递增，旧缓存自动用新参数重生成。 */
    private static final String TTS_CACHE_VERSION = "v2";

    private String generateViaDirect(String text, String voice) {
        String voiceKey = (voice == null || voice.isBlank()) ? "default" : voice.trim();
        String filename = md5(TTS_CACHE_VERSION + "|" + text + "|" + voiceKey) + ".wav";
        Path dir = Paths.get(uploadDir, "tts");
        Path out = dir.resolve(filename);
        // 缓存命中：直接返回已有音频
        try {
            if (Files.exists(out) && Files.size(out) > 0) {
                log.info("TTS 缓存命中({}): {}", voiceKey, text.substring(0, Math.min(20, text.length())));
                return "/uploads/tts/" + filename;
            }
        } catch (Exception ignore) {
        }
        log.info("TTS 直连生成开始({}): {}", voiceKey, text.substring(0, Math.min(20, text.length())));
        try {
            ensureDirectServerUp();
            Files.createDirectories(dir);

            // 生成请求用长超时的 RestTemplate（推理较慢）
            RestTemplate tts = new RestTemplate();
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout((int) chatTtsConfig.getDirectRequestTimeout());
            tts.setRequestFactory(factory);

            // 显式 Content-Length：tts_server.py 用 stdlib http.server，只支持固定长度请求体
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("voice", voiceKey);
            byte[] payload = objectMapper.writeValueAsBytes(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentLength(payload.length);
            HttpEntity<byte[]> request = new HttpEntity<>(payload, headers);

            ResponseEntity<byte[]> response = tts.postForEntity(
                    chatTtsConfig.getDirectUrl() + "/generate", request, byte[].class);
            byte[] data = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || data == null || data.length == 0) {
                log.warn("TTS 直连服务返回异常: HTTP {}", response.getStatusCode());
                throw new BizException("语音服务暂不可用");
            }
            Files.write(out, data);
            log.info("TTS 直连生成完成: {} ({}KB)", filename, data.length / 1024);
            return "/uploads/tts/" + filename;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TTS 直连调用失败: {}", e.getMessage());
            throw new BizException("语音服务暂不可用");
        }
    }

    /** 直连服务未启动则拉起，并等待就绪 */
    private void ensureDirectServerUp() {
        if (isDirectServerUp()) {
            return;
        }
        synchronized (directStartLock) {
            if (isDirectServerUp()) {
                return;
            }
            spawnDirectServer();
            long deadline = System.currentTimeMillis() + chatTtsConfig.getDirectStartupTimeout();
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BizException("语音服务启动被中断");
                }
                if (isDirectServerUp()) {
                    log.info("TTS 直连服务已就绪");
                    return;
                }
            }
            throw new BizException("语音服务启动超时，请稍后再试");
        }
    }

    /** 探活：GET /ping */
    private boolean isDirectServerUp() {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2000);
            factory.setReadTimeout(2000);
            RestTemplate ping = new RestTemplate(factory);
            ResponseEntity<String> resp = ping.getForEntity(
                    chatTtsConfig.getDirectUrl() + "/ping", String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /** 后台拉起 tts_server.py（进程独立于后端存活，模型只加载一次） */
    private void spawnDirectServer() {
        try {
            Path script = Paths.get(chatTtsConfig.getDirectScript());
            if (!Files.exists(script)) {
                // 脚本路径配置错误时快速失败，避免每个音色都白等启动超时
                throw new BizException("TTS 直连脚本不存在: " + script);
            }
            List<String> cmd = java.util.List.of(
                    chatTtsConfig.getDirectPython(),
                    chatTtsConfig.getDirectScript(),
                    "--port", String.valueOf(chatTtsConfig.getDirectPort()));
            log.info("拉起 TTS 直连服务: {}", String.join(" ", cmd));
            Path logDir = Paths.get("logs");
            Files.createDirectories(logDir);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logDir.resolve("tts-server.log").toFile()));
            if (!chatTtsConfig.getAppRoot().isBlank()) pb.environment().put("TTS_APP_ROOT", chatTtsConfig.getAppRoot());
            if (!chatTtsConfig.getModelCache().isBlank()) pb.environment().put("HF_HOME", chatTtsConfig.getModelCache());
            pb.start();
            // 不等待：进程常驻后台，就绪探测由 ensureDirectServerUp 轮询 /ping 完成
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("拉起 TTS 直连服务失败: {}", e.getMessage());
            throw new BizException("语音服务启动失败");
        }
    }

    /** 文本 MD5（用于桥接模式缓存文件名） */
    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 必然可用，兜底用时间戳
            return String.valueOf(System.currentTimeMillis());
        }
    }

    /**
     * 清洗文本供 TTS 朗读：去除 Markdown 标记、多余星号/井号。
     * 即使 DeepSeek 已清洗，课程内容等来源的文本也需兜底处理。
     */
    public static String cleanTextForTts(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text
                // 去 HTML 标签
                .replaceAll("<[^>]+>", " ")
                // 去粗体/斜体
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*{2,}", "")
                .replaceAll("(?<!\\*)\\*(?!\\*)", "")
                .replaceAll("_{2,}", "")
                .replaceAll("(?<!_)_(?!_)", "")
                // 去标题井号
                .replaceAll("#{1,6}\\s*", "")
                // 去行内代码和代码块围栏
                .replaceAll("`{1,3}", "")
                // 去链接，保留文字
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                // 去引用前缀和列表标记
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "")
                .replaceAll("(?m)^\\s*\\d+\\.\\s+", "")
                // 合并多余空白
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * HTTP 直连模式：POST 到 ChatTTS 服务
     */
    private final Object[] httpCacheLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    private String generateViaHttp(String text, Double speed, String voice, String rate, String volume, String pitch) {
        String voiceKey = voice == null || voice.isBlank() ? "default" : voice;
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("voice", voiceKey);
        if (speed != null) {
            body.put("speed", speed);
        }
        if (rate != null && !rate.isBlank()) body.put("rate", rate);
        if (volume != null && !volume.isBlank()) body.put("volume", volume);
        if (pitch != null && !pitch.isBlank()) body.put("pitch", pitch);
        String cacheKey;
        try {
            cacheKey = md5(new ObjectMapper().writeValueAsString(java.util.List.of(
                    chatTtsConfig.getApiUrl(), chatTtsConfig.getCacheVersion(), new java.util.TreeMap<>(body))));
        } catch (Exception e) {
            throw new BizException("语音缓存参数错误");
        }
        synchronized (httpCacheLocks[Math.floorMod(cacheKey.hashCode(), httpCacheLocks.length)]) {
            return requestHttpAudio(body, cacheKey);
        }
    }

    private String requestHttpAudio(Map<String, Object> body, String cacheKey) {
        Path dir = Paths.get(uploadDir, "tts");
        try {
            for (String ext : List.of("wav", "mp3", "ogg")) {
                Path cached = dir.resolve("http-" + cacheKey + "." + ext);
                if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
                    return "/uploads/tts/" + cached.getFileName();
                }
            }
        } catch (Exception e) {
            throw new BizException("语音缓存读取失败");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!chatTtsConfig.getApiKey().isBlank()) headers.setBearerAuth(chatTtsConfig.getApiKey());

        try {
            // Python stdlib 服务只读取 Content-Length，不支持 chunked 请求。
            byte[] json = new ObjectMapper().writeValueAsBytes(body);
            headers.setContentLength(json.length);
            HttpEntity<byte[]> request = new HttpEntity<>(json, headers);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(chatTtsConfig.getConnectTimeout());
            factory.setReadTimeout(chatTtsConfig.getRequestTimeout());
            ResponseEntity<byte[]> response = new RestTemplate(factory).exchange(
                    chatTtsConfig.getApiUrl(), HttpMethod.POST, request, byte[].class);
            byte[] data = response.getBody();
            if (data == null || data.length == 0) {
                throw new BizException("语音服务返回为空");
            }
            String contentType = response.getHeaders().getContentType() == null
                    ? "" : response.getHeaders().getContentType().toString();
            if (contentType.contains("audio") || contentType.contains("octet-stream")) {
                // 仅缓存可识别格式，避免将错误响应或 MP3 存成 WAV。
                String ext = audioExtension(data);
                if (ext == null) throw new BizException("语音服务返回了不支持的音频格式");
                String filename = "http-" + cacheKey + "." + ext;
                Files.createDirectories(dir);
                Path tmp = Files.createTempFile(dir, "http-", ".tmp");
                try {
                    Files.write(tmp, data);
                    try {
                        Files.move(tmp, dir.resolve(filename), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                        Files.move(tmp, dir.resolve(filename), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(tmp);
                }
                return "/uploads/tts/" + filename;
            }
            // JSON 响应：透传 audio_url
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(data);
            JsonNode audioUrl = node.get("audio_url");
            if (audioUrl != null && !audioUrl.isNull() && !audioUrl.asText().isEmpty()) {
                return audioUrl.asText();
            }
            throw new BizException("语音服务返回异常，未包含音频地址");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TTS 服务调用失败: {}", e.getMessage());
            throw new BizException("语音服务暂不可用");
        }
    }

    public static String audioExtension(byte[] data) {
        if (data.length >= 12 && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WAVE")) return "wav";
        if (data.length >= 4 && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("OggS")) return "ogg";
        if (data.length >= 3 && (new String(data, 0, 3, StandardCharsets.US_ASCII).equals("ID3")
                || ((data[0] & 0xff) == 0xff && (data[1] & 0xe0) == 0xe0))) return "mp3";
        return null;
    }
}
