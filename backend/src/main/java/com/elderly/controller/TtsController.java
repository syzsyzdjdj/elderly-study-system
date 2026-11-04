package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.config.TtsVoices;
import com.elderly.dto.TtsDTO;
import com.elderly.entity.SysUser;
import com.elderly.service.SysUserService;
import com.elderly.service.TtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 语音合成（调用本地 ChatTTS 服务，需登录）
 */
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;
    private final SysUserService sysUserService;
    private final com.elderly.config.ChatTtsConfig chatTtsConfig;

    /** 获取全部语音包（个人中心切换音色用） */
    @GetMapping("/voices")
    public Result<Map<String, Object>> voices() {
        boolean available = !"system".equalsIgnoreCase(chatTtsConfig.getMode());
        return Result.success(Map.of("packs", TtsVoices.PACKS,
                "generationAvailable", available,
                "unavailableReason", available ? "" : "当前服务器使用已有录音与设备朗读模式，未启用外部 TTS 合成服务，因此无法生成所选音色。"));
    }

    /**
     * 设置当前登录用户的朗读音色（个人中心语音包切换）。
     * 不走 /api/user/update（该接口仅管理员可用），只允许改自己的 voice。
     */
    @PutMapping("/voice")
    public Result<Void> updateVoice(@RequestBody Map<String, String> body) {
        String voice = body == null ? null : body.get("voice");
        boolean valid = voice != null && TtsVoices.PACKS.stream()
                .flatMap(p -> ((List<Map<String, Object>>) p.get("voices")).stream())
                .anyMatch(v -> voice.equals(v.get("key")));
        if (!valid) {
            throw new BizException("无效的音色");
        }
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        SysUser update = new SysUser();
        update.setId(userId);
        update.setVoice(voice);
        sysUserService.updateUser(update);
        return Result.success();
    }

    /** 文本转语音，返回 {url} */
    @PostMapping("/segments")
    public Result<Map<String, Object>> segments(@Valid @RequestBody TtsDTO dto) {
        return Result.success(Map.of("segments", com.elderly.util.TtsSegments.split(dto.getText())));
    }

    @PostMapping("/generate")
    public Result<Map<String, String>> generate(@Valid @RequestBody TtsDTO dto) {
        String url = ttsService.generate(dto.getText(), dto.getSpeed(), dto.getVoice(), null, null, null);
        return Result.success(Map.of("url", url));
    }
}
