package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.config.DeepSeekConfig;
import com.elderly.dto.ChatTurn;
import com.elderly.entity.FaqQa;
import com.elderly.mapper.AiQuestionRecordMapper;
import com.elderly.util.DeepSeekUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiChatRoutingTest {
    final DeepSeekUtil llm = mock(DeepSeekUtil.class);
    final SysConfigService settings = mock(SysConfigService.class);
    final FaqQaService faqs = mock(FaqQaService.class);
    final AiChatServiceImpl service = new AiChatServiceImpl(mock(AiQuestionRecordMapper.class), llm,
            new DeepSeekConfig(), mock(PointService.class), settings, faqs, new LocalKnowledgeService(new ObjectMapper()));

    @Test void faqRemainsFirstWithoutCallingProvider() {
        var faq = new FaqQa(); faq.setId(1L); faq.setAnswer("已审核内容");
        when(faqs.match("问题")).thenReturn(faq);
        assertEquals("FAQ", service.chat(null, "问题").source());
        verifyNoInteractions(llm);
    }

    @Test void distinguishesDisabledUnconfiguredProviderFailureAndRealModelReply() {
        assertTrue(service.chat(null, "扫码付款").notice().contains("未启用"));
        when(settings.getBool("ai_enabled", true)).thenReturn(true);
        assertTrue(service.chat(null, "扫码付款").notice().contains("未配置"));
        when(llm.hasUsableKey()).thenReturn(true);
        var history = List.of(new ChatTurn("user", "哪个城市"));
        when(llm.ask("广州", history)).thenReturn("上下文答复");
        var ai = service.chat(null, "广州", history);
        assertEquals("AI", ai.source()); assertEquals("", ai.notice());
        verify(llm).ask("广州", history);
        when(llm.ask("继续", List.of())).thenThrow(new BizException("provider unavailable"));
        var fallback = service.chat(null, "继续");
        assertEquals("LOCAL", fallback.source());
        assertTrue(fallback.notice().contains("暂时不可用"));
    }
}
