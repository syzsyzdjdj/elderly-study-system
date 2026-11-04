package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.service.impl.TtsPrewarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 老人端语音预热（ADMIN）：一键把老人界面的课程、政策、消息、陪伴话术、奖品等
 * 朗读文本提前合成到 TTS 缓存，老人收听时秒回，无需等待“语音生成中”。
 */
@RestController
@RequestMapping("/api/tts/prewarm")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TtsPrewarmController {

    private final TtsPrewarmService ttsPrewarmService;

    @GetMapping("/course-manifest")
    public Result<java.util.List<Map<String, String>>> courseManifest() {
        return Result.success(ttsPrewarmService.courseManifest());
    }

    /** 启动预热；body: {"mode": "default|local|all|具体音色key"}，缺省 default */
    @PostMapping
    public Result<Map<String, Object>> start(@RequestBody(required = false) Map<String, String> body) {
        String mode = body == null ? null : body.get("mode");
        ttsPrewarmService.start(mode);
        return Result.success(ttsPrewarmService.progress());
    }

    /** 查询预热进度 */
    @GetMapping("/progress")
    public Result<Map<String, Object>> progress() {
        return Result.success(ttsPrewarmService.progress());
    }
}
