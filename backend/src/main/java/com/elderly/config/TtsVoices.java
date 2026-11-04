package com.elderly.config;

import java.util.List;
import java.util.Map;

/**
 * 语音包定义：普通话=本地 ChatTTS（离线），方言=edge-tts（在线，需联网）。
 * 课程保存后按全部音色预生成朗读音频，学员切换任一音色即可秒播。
 * preview 为预生成好的试听音频（前端 public/voice-previews/ 静态文件，点击即播无需生成）。
 */
public final class TtsVoices {

    private TtsVoices() {
    }

    public static final List<Map<String, Object>> PACKS = List.of(
            Map.of("name", "普通话（本地生成）", "voices", List.of(
                    voice("default", "默认音色"),
                    voice("elderly", "中老年人"),
                    voice("female", "女声"),
                    voice("male1", "男声1"),
                    voice("male", "男声"),
                    voice("stable", "稳定音色"))),
            Map.of("name", "普通话·在线（女声）", "voices", List.of(
                    voice("edge:xx-female", "晓晓·女声（温暖）"),
                    voice("edge:xy-female", "晓伊·女声（活泼）"),
                    voice("edge:xc-female", "晓辰·女声（温柔）"),
                    voice("edge:xh-female", "晓涵·女声（大气）"),
                    voice("edge:xq-female", "晓秋·女声（亲切）"),
                    voice("edge:xr-female", "晓睿·女声（灵动）"),
                    voice("edge:xm-female", "晓梦·女声（梦幻）"))),
            Map.of("name", "普通话·在线（男声）", "voices", List.of(
                    voice("edge:yj-male", "云健·男声（激昂）"),
                    voice("edge:yx-male", "云希·男声（阳光）"),
                    voice("edge:yxiao-male", "云夏·男声（可爱）"),
                    voice("edge:yy-male", "云扬·男声（专业）"),
                    voice("edge:yf-male", "云枫·男声（沉稳）"),
                    voice("edge:yz-male", "云泽·男声（浑厚）"),
                    voice("edge:yh-male", "云皓·男声（明朗）"))),
            Map.of("name", "方言·东北/陕西", "voices", List.of(
                    voice("edge:dongbei", "东北话·女声"),
                    voice("edge:shaanxi", "陕西话·女声"))),
            Map.of("name", "粤语（广东/香港）", "voices", List.of(
                    voice("edge:cantonese", "粤语·女声（晓佳）"),
                    voice("edge:cantonese-female2", "粤语·女声（晓曼）"),
                    voice("edge:cantonese-male", "粤语·男声（云龙）"))),
            Map.of("name", "台湾话（闽南语）", "voices", List.of(
                    voice("edge:tw-female", "台湾话·女声（晓臻）"),
                    voice("edge:tw-female2", "台湾话·女声（晓雨）"),
                    voice("edge:tw-male", "台湾话·男声（云哲）"))));

    private static Map<String, Object> voice(String key, String name) {
        return Map.of("key", key, "name", name,
                "preview", "/voice-previews/" + key.replace(':', '_') + ".mp3");
    }


    /** 全部音色 key（课程语音预生成用） */
    public static List<String> allKeys() {
        return PACKS.stream()
                .flatMap(p -> ((List<Map<String, Object>>) p.get("voices")).stream())
                .map(v -> (String) v.get("key"))
                .toList();
    }
}
