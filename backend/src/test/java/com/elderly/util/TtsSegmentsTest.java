package com.elderly.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TtsSegmentsTest {
    @Test void preservesTextAndBoundsFirstAndFollowingSegments() {
        String text = "这是一个需要拆分的长回答".repeat(40) + "。最后一句话也需要被朗读，不能被遗漏。";
        var parts = TtsSegments.split(text);
        assertEquals(ChatTtsUtil.cleanTextForTts(text), String.join("", parts));
        assertTrue(parts.get(0).length() <= 60);
        assertTrue(parts.stream().allMatch(p -> p.codePointCount(0, p.length()) <= 120));
    }

    @Test void handlesMarkdownEmptyTextAndSurrogatePairs() {
        assertTrue(TtsSegments.split(" ").isEmpty());
        String text = "😀".repeat(200);
        assertEquals(text, String.join("", TtsSegments.split(text)));
        assertEquals("欢迎学习手机支付。", String.join("", TtsSegments.split("**欢迎**学习手机支付。")));
    }
}
