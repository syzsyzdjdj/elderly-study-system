package com.elderly.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalKnowledgeServiceTest {
    private final LocalKnowledgeService knowledge = new LocalKnowledgeService(new ObjectMapper());

    @Test void explicitFraudTermsOutrankPhoneOperationKeywords() {
        String answer = knowledge.answer("有人视频通话说我是洗钱嫌疑人，要转到安全账户");
        assertTrue(answer.startsWith("防诈骗提醒"));
        assertTrue(answer.contains("安全账户"));
        assertFalse(answer.contains("点击右下角"));
    }

    @Test void normalizesLatinCaseAndWidthWithoutMatchingInsideAnotherWord() {
        assertTrue(knowledge.answer("怎么连接ＷＩＦＩ").contains("打开手机"));
        assertTrue(knowledge.answer("HELLO").startsWith("您好"));
        assertFalse(knowledge.answer("this is unclear").startsWith("您好"));
        assertTrue(knowledge.answer("买火车票APP怎么用").contains("12306"));
        assertTrue(knowledge.answer("打车APP怎么用").contains("车牌号"));
        assertTrue(knowledge.answer("12306怎么预约").contains("火车票"));
    }
}
