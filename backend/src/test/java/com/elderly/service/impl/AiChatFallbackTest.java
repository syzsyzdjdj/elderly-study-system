package com.elderly.service.impl;

import com.elderly.config.DeepSeekConfig;
import com.elderly.mapper.AiQuestionRecordMapper;
import com.elderly.util.DeepSeekUtil;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AiChatFallbackTest {
    @Test void paymentFallbackDistinguishesReceivingAndPayingWithoutCallingLlm() {
        var llm = mock(DeepSeekUtil.class);
        var service = new AiChatServiceImpl(mock(AiQuestionRecordMapper.class), llm, new DeepSeekConfig(),
                mock(PointService.class), mock(SysConfigService.class), mock(FaqQaService.class),
                new LocalKnowledgeService(new com.fasterxml.jackson.databind.ObjectMapper()));
        String answer = service.chat(null, "扫码支付和收款怎么操作").answer();
        assertTrue(answer.contains("收款码"));
        assertTrue(answer.contains("付款码用于向商家付款，不是收款码"));
        assertFalse(answer.contains("收款时点击"));
        verifyNoInteractions(llm);
    }
}
