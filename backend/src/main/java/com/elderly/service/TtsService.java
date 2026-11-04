package com.elderly.service;

/**
 * 语音合成服务
 */
public interface TtsService {

    /**
     * 调用 ChatTTS 生成语音
     *
     * @param text   文本
     * @param speed  语速(可为空)
     * @param voice  音色(可为空，默认音色)
     * @param rate   语速百分比(可为空，如 "-15%"，仅 edge-tts 模式生效)
     * @param volume 音量百分比(可为空，如 "+50%"，仅 edge-tts 模式生效)
     * @param pitch  音调(可为空，如 "+10Hz"，仅 edge-tts 模式生效)
     * @return 音频地址（远程 audio_url 或本地 /uploads/tts/xxx.wav）
     */
    String generate(String text, Double speed, String voice, String rate, String volume, String pitch);
}