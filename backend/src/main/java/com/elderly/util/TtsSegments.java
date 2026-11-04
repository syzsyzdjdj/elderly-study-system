package com.elderly.util;

import java.util.ArrayList;
import java.util.List;

/** 同一分段规则用于在线播放与课程预生成，保证缓存一致。 */
public final class TtsSegments {
    private TtsSegments() {}

    public static List<String> split(String text) {
        String clean = ChatTtsUtil.cleanTextForTts(text);
        if (clean == null || clean.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (int cp : clean.codePoints().toArray()) {
            part.appendCodePoint(cp);
            int length = part.codePointCount(0, part.length());
            // 首段尽早开始播放，后续段稍长以减少请求和句间停顿。
            int limit = result.isEmpty() ? 60 : 120;
            if (length >= limit || ("。！？!?；;\n".indexOf(cp) >= 0 && length >= 12)) {
                result.add(part.toString().trim());
                part.setLength(0);
            }
        }
        if (!part.toString().isBlank()) result.add(part.toString().trim());
        return result;
    }
}
