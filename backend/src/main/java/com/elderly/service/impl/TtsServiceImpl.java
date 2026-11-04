package com.elderly.service.impl;

import com.elderly.service.TtsService;
import com.elderly.util.ChatTtsUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 语音合成服务实现：
 * 音色/语速/音调/音量缺省时读取后台 sys_config（tts_default_voice / tts_speed / tts_rate / tts_volume / tts_pitch），
 * 前端或用户已显式指定时以传入值为准。
 */
@Service
@RequiredArgsConstructor
public class TtsServiceImpl implements TtsService {

    private final ChatTtsUtil chatTtsUtil;
    private final SysConfigService sysConfigService;

    @Override
    public String generate(String text, Double speed, String voice, String rate, String volume, String pitch) {
        if (voice == null || voice.isBlank()) {
            voice = sysConfigService.get("tts_default_voice", "default");
        }
        if (speed == null) {
            speed = sysConfigService.getDouble("tts_speed", 1.0);
        }
        // edge-tts 专属参数：后台未配则不传，使用 edge-tts 默认值
        if (rate == null) {
            rate = sysConfigService.get("tts_rate", "");
        }
        if (volume == null) {
            volume = sysConfigService.get("tts_volume", "");
        }
        if (pitch == null) {
            pitch = sysConfigService.get("tts_pitch", "");
        }
        // 在线音色（edge-tts）：老人端语速按钮三档（0.8 慢速 / 1.0 标准 / 1.3 快速）映射为 edge rate，
        // 老人显式选择慢/快速时优先于后台默认 rate；缓存 key 随 rate 变化，三档版本各自独立。
        if (voice.startsWith("edge:")) {
            String speedRate = mapSpeedToEdgeRate(speed);
            if (speedRate != null) {
                rate = speedRate;
            }
        }
        return chatTtsUtil.generate(text, speed, voice, rate, volume, pitch);
    }

    /**
     * 老人端语速档位 → edge-tts rate 百分比。
     * 0.8x→-20%，1.3x→+30%；标准档（1.0）返回 null，沿用后台 tts_rate 配置。
     * 注意：本地 ChatTTS 不支持变速，该映射仅对 edge 在线音色生效。
     */
    public static String mapSpeedToEdgeRate(Double speed) {
        if (speed == null) {
            return null;
        }
        if (speed <= 0.85) {
            return "-20%";
        }
        if (speed >= 1.2) {
            return "+30%";
        }
        return null;
    }
}
