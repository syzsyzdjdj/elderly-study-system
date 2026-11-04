package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.config.TtsVoices;
import com.elderly.config.ChatTtsConfig;
import com.elderly.common.BizException;
import com.elderly.util.TtsSegments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import com.elderly.entity.AiCompanionScript;
import com.elderly.entity.CourseInfo;
import com.elderly.entity.GovPolicy;
import com.elderly.entity.Prize;
import com.elderly.entity.UserMessage;
import com.elderly.mapper.AiCompanionScriptMapper;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.GovPolicyMapper;
import com.elderly.mapper.PrizeMapper;
import com.elderly.mapper.UserMessageMapper;
import com.elderly.service.TtsService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 老人端语音统一预热服务：
 * 把老人界面可枚举的朗读内容（课程正文/课程简介、政务政策、消息通知、
 * 陪伴话术、积分奖品）按选定音色提前合成到 uploads/tts 缓存，
 * 老人点击“听全文/点读”时直接命中缓存秒播，无需等待生成。
 * 单线程串行执行（本地 ChatTTS 单实例同时合成一条最稳），全程异步不阻塞接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsPrewarmService {

    private final CourseInfoMapper courseInfoMapper;
    private final GovPolicyMapper govPolicyMapper;
    private final UserMessageMapper userMessageMapper;
    private final AiCompanionScriptMapper aiCompanionScriptMapper;
    private final PrizeMapper prizeMapper;
    private final TtsService ttsService;
    private final SysConfigService sysConfigService;
    private final ChatTtsConfig chatTtsConfig;

    /** 不阻塞网站启动；生产环境可选择仅模型预热或同时预生成固定内容。 */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!generationAvailable()) return;
        if (!chatTtsConfig.isStartupWarmup() && !chatTtsConfig.isPrewarmOnStartup()) return;
        executor.submit(() -> {
            try {
                if (chatTtsConfig.isStartupWarmup()) {
                    // 独立服务可能仍在加载模型，给它一个启动宽限期。
                    long deadline = System.nanoTime()
                            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(chatTtsConfig.getDirectStartupTimeout());
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            ttsService.generate("您好，语音服务已准备好。", null, "default", null, null, null);
                            break;
                        } catch (Exception e) {
                            if (System.nanoTime() >= deadline) throw e;
                            Thread.sleep(3000);
                        }
                    }
                    if (Thread.currentThread().isInterrupted()) return;
                }
                if (chatTtsConfig.isPrewarmOnStartup() && !running) start("default");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("TTS 启动预热失败，可通过管理端重试", e);
            }
        });
    }

    /** 预热执行线程：全 JVM 单线程串行，避免并发合成压垮本地 TTS 服务 */
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "tts-prewarm");
                t.setDaemon(true);
                return t;
            });

    // ===== 进度状态（内存即可，重启后为空，管理员重新触发预热即可）=====
    private volatile boolean running = false;
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private volatile String currentLabel = "";
    private volatile String voiceMode = "";
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    /** 最近若干条失败原因，供管理端展示 */
    private final List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_ERRORS = 20;

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    /** 当前进度快照 */
    public Map<String, Object> progress() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generationAvailable", generationAvailable());
        m.put("unavailableReason", generationAvailable() ? "" : SYSTEM_MODE_REASON);
        m.put("running", running);
        m.put("total", total.get());
        m.put("done", done.get());
        m.put("failed", failed.get());
        m.put("currentLabel", currentLabel);
        m.put("voiceMode", voiceMode);
        m.put("startedAt", startedAt);
        m.put("finishedAt", finishedAt);
        synchronized (errors) {
            m.put("errors", new ArrayList<>(errors));
        }
        return m;
    }

    private static final String SYSTEM_MODE_REASON = "当前服务器使用预录音频与设备朗读，未启用语音合成服务，不能一键生成音频。已有课程录音仍可播放；缺失内容可导出课程清单后生成并上传，或先配置语音合成服务。";

    private boolean generationAvailable() {
        return !"system".equalsIgnoreCase(chatTtsConfig.getMode());
    }

    /**
     * 启动一次预热（已在运行时拒绝）。
     *
     * @param mode 音色范围：default=系统默认音色；local=全部本地普通话音色；
     *             all=全部音色（含在线 edge 方言，耗时很长且需联网）；
     *             其他值当作具体音色 key（如 edge:dongbei）
     */
    public synchronized void start(String mode) {
        // Reject before collecting content or creating thousands of inevitably failing jobs.
        if (!generationAvailable()) throw new BizException(SYSTEM_MODE_REASON);
        if (running) {
            throw new IllegalStateException("预热任务正在执行中");
        }
        List<String> voices = resolveVoices(mode);
        // 先在调用线程收集条目（快），再异步合成
        List<Map.Entry<String, String>> items = collectItems();
        int variants = voices.size() * 3;
        running = true;
        total.set(items.size() * variants);
        done.set(0);
        failed.set(0);
        currentLabel = "排队中";
        this.voiceMode = mode == null || mode.isBlank() ? "default" : mode;
        startedAt = LocalDateTime.now();
        finishedAt = null;
        errors.clear();
        log.info("TTS 预热开始：{} 条文本，音色×语速共 {} 个版本，合计 {} 项",
                items.size(), variants, total.get());
        executor.submit(() -> run(items, voices));
    }

    /** 解析音色范围 */
    private List<String> resolveVoices(String mode) {
        if (mode == null || mode.isBlank() || "default".equalsIgnoreCase(mode)) {
            return List.of(sysConfigService.get("tts_default_voice", "default"));
        }
        if ("local".equalsIgnoreCase(mode)) {
            return TtsVoices.PACKS.get(0).get("voices") instanceof List<?> list
                    ? list.stream().map(v -> (String) ((Map<?, ?>) v).get("key")).toList()
                    : List.of("default");
        }
        if ("all".equalsIgnoreCase(mode)) {
            return TtsVoices.allKeys();
        }
        // 显式音色 key：校验合法性，防止注入到进程调用参数
        boolean valid = TtsVoices.allKeys().contains(mode);
        if (!valid) {
            throw new IllegalArgumentException("无效的音色: " + mode);
        }
        return List.of(mode);
    }

    /** 收集老人端全部可朗读文本，条目 = 标签(展示用) -> 文本(与前端拼接完全一致) */
    private List<Map.Entry<String, String>> collectItems() {
        // 文本去重：不同入口可能出现相同句子，只合成一次
        Map<String, String> unique = new LinkedHashMap<>();
        addItem(unique, "演示提醒", "欢迎使用老年数字素养学习系统，点击任意文字即可收听语音朗读。");

        // 1. 课程：学习页“听全文”（正文，与 Study.vue listenText 一致）+ 详情页标题简介
        List<CourseInfo> courses = courseInfoMapper.selectList(
                new QueryWrapper<CourseInfo>().eq("status", 1).orderByAsc("id"));
        JsonNode tutorials;
        try (var input = new ClassPathResource("tts/course-tutorials.json").getInputStream()) {
            tutorials = new ObjectMapper().readTree(input);
        } catch (Exception e) {
            throw new IllegalStateException("课程朗读共享资源加载失败", e);
        }
        for (CourseInfo c : courses) {
            String body = toListenText(c, tutorials.path(String.valueOf(c.getId())));
            if (!body.isBlank()) {
                addItem(unique, "课程正文《" + c.getTitle() + "》", body);
            }
            String intro = (c.getIntro() == null || c.getIntro().isBlank())
                    ? "暂无简介" : c.getIntro().trim();
            addItem(unique, "课程简介《" + c.getTitle() + "》",
                    (c.getTitle() == null ? "" : c.getTitle().trim()) + "。" + intro);
        }

        // 2. 政务政策：GovCenter 弹窗 title + content
        List<GovPolicy> policies = govPolicyMapper.selectList(
                new QueryWrapper<GovPolicy>().eq("status", 1).orderByDesc("publish_time"));
        for (GovPolicy p : policies) {
            String content = (p.getContent() == null || p.getContent().isBlank())
                    ? "暂无政策正文" : p.getContent().trim();
            addItem(unique, "政策《" + p.getTitle() + "》",
                    (p.getTitle() == null ? "" : p.getTitle().trim()) + "。" + content);
        }

        // 3. 用户消息通知：Messages 页 title + content
        List<UserMessage> messages = userMessageMapper.selectList(
                new QueryWrapper<UserMessage>().orderByDesc("id"));
        for (UserMessage m : messages) {
            String title = m.getTitle() == null ? "" : m.getTitle().trim();
            String content = m.getContent() == null ? "" : m.getContent().trim();
            if (title.isEmpty() && content.isEmpty()) {
                continue;
            }
            addItem(unique, "消息：" + (title.isEmpty() ? content.substring(0, Math.min(12, content.length())) : title),
                    title + (title.isEmpty() ? "" : "。") + content);
        }

        // 4. AI 陪伴话术：Companion 页直接朗读 content
        List<AiCompanionScript> scripts = aiCompanionScriptMapper.selectList(
                new QueryWrapper<AiCompanionScript>().eq("enabled", 1).orderByAsc("sort"));
        for (AiCompanionScript s : scripts) {
            if (s.getContent() != null && !s.getContent().isBlank()) {
                addItem(unique, "陪伴话术：" + (s.getTitle() == null ? s.getId() : s.getTitle()),
                        s.getContent().trim());
            }
        }

        // 5. 积分奖品：Prizes 页 name + description
        List<Prize> prizes = prizeMapper.selectList(
                new QueryWrapper<Prize>().eq("status", 1).orderByAsc("points_cost"));
        for (Prize p : prizes) {
            String desc = (p.getDescription() == null || p.getDescription().isBlank())
                    ? "积分专享福利" : p.getDescription().trim();
            addItem(unique, "奖品《" + p.getName() + "》",
                    (p.getName() == null ? "" : p.getName().trim()) + "。" + desc);
        }
        return new ArrayList<>(unique.entrySet());
    }

    /** 仅导出公开课程，不将私人消息或AI问答交给离线生成工具。 */
    public List<Map<String, String>> courseManifest() {
        try (var input = new ClassPathResource("tts/course-tutorials.json").getInputStream()) {
            JsonNode tutorials = new ObjectMapper().readTree(input);
            Map<String, Map<String, String>> segments = new LinkedHashMap<>();
            for (CourseInfo c : courseInfoMapper.selectList(new QueryWrapper<CourseInfo>().eq("status", 1).orderByAsc("id"))) {
                String body = toListenText(c, tutorials.path(String.valueOf(c.getId())));
                String intro = (c.getTitle() == null ? "" : c.getTitle().trim()) + "。"
                        + (c.getIntro() == null || c.getIntro().isBlank() ? "暂无简介" : c.getIntro().trim());
                List<String> texts = new ArrayList<>(List.of(body, intro));
                JsonNode tutorial = tutorials.path(String.valueOf(c.getId()));
                for (JsonNode step : tutorial.path("steps")) {
                    texts.add(step.path("title").asText() + "。" + step.path("action").asText()
                            + "。" + step.path("check").asText());
                }
                for (String text : texts) for (String part : TtsSegments.split(text)) {
                    String clean = com.elderly.util.ChatTtsUtil.cleanTextForTts(part);
                    if (clean.isBlank()) continue;
                    String key = com.elderly.util.PreparedTtsAudio.key(clean);
                    segments.putIfAbsent(key, Map.of("key", key, "text", clean,
                            "courseId", String.valueOf(c.getId()), "title", c.getTitle()));
                }
            }
            return new ArrayList<>(segments.values());
        } catch (Exception e) { throw new IllegalStateException("导出课程朗读清单失败", e); }
    }

    /** 与前端 Study.vue listenText 完全一致：去 HTML 标签、合并空白、空则回退简介/标题 */
    static String toListenText(CourseInfo course, JsonNode tutorial) {
        if (!tutorial.isMissingNode() && !tutorial.isNull()) {
            List<String> parts = new ArrayList<>();
            parts.add(tutorial.path("goal").asText());
            parts.add(tutorial.path("preparation").asText());
            int index = 1;
            for (JsonNode step : tutorial.path("steps")) {
                parts.add("第" + index++ + "步，" + step.path("title").asText() + "。"
                        + step.path("action").asText() + "。" + step.path("check").asText());
            }
            parts.add(tutorial.path("warning").asText());
            parts.add(tutorial.path("practice").asText());
            return String.join("。", parts);
        }
        String text = course.getContent() == null ? "" : course.getContent()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty() && course.getIntro() != null) {
            text = course.getIntro().trim();
        }
        if (text.isEmpty() && course.getTitle() != null) {
            text = course.getTitle().trim();
        }
        return text;
    }

    private static void addItem(Map<String, String> unique, String label, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        unique.putIfAbsent(text, label);
    }

    /**
     * 串行合成：同一文本对"音色×语速"各版本逐个生成；单条失败不影响后续。
     * 按播放器请求预生成慢/标准/快三档（0.8/1.0/1.3）；
     * direct 模式会复用同音色文件，HTTP 模式按实际发送的参数区分缓存。
     * 全程走 TtsService（与老人播放同一路径），自动应用后台配置的默认参数，保证缓存 key 一致。
     */
    private void run(List<Map.Entry<String, String>> items, List<String> voices) {
        try {
            for (Map.Entry<String, String> item : items) {
                for (String voice : voices) {
                    for (Double speed : speedVariants(voice)) {
                        if (!running) {
                            return;
                        }
                        String sp = speedLabel(speed);
                        currentLabel = item.getValue() + "（" + voice + (sp.isEmpty() ? "" : "·" + sp) + "）";
                        try {
                            for (String segment : TtsSegments.split(item.getKey())) {
                                if (Thread.currentThread().isInterrupted()) return;
                                ttsService.generate(segment, speed, voice, null, null, null);
                            }
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            String err = item.getValue() + " [" + voice
                                    + (sp.isEmpty() ? "" : "·" + sp) + "] " + e.getMessage();
                            log.warn("TTS 预热失败：{}", err);
                            errors.add(err);
                            if (errors.size() > MAX_ERRORS) {
                                errors.remove(0);
                            }
                        } finally {
                            done.incrementAndGet();
                        }
                    }
                }
            }
            log.info("TTS 预热完成：成功 {}，失败 {}，共 {}",
                    done.get() - failed.get(), failed.get(), total.get());
        } finally {
            currentLabel = failed.get() > 0 ? "完成（有失败项）" : "全部完成";
            finishedAt = LocalDateTime.now();
            running = false;
        }
    }

    /** 与播放器请求保持一致；本地模式自动复用相同音频。 */
    private static List<Double> speedVariants(String voice) {
        // HTTP 请求包含 speed，三个版本均预生成以匹配播放器请求。
        return List.of(0.8, 1.0, 1.3);
    }

    private static String speedLabel(Double speed) {
        if (speed == null) {
            return "";
        }
        if (speed <= 0.85) {
            return "慢速";
        }
        if (speed >= 1.2) {
            return "快速";
        }
        return "标准";
    }
}
