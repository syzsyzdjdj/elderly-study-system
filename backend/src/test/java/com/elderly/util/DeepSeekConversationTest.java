package com.elderly.util;

import com.elderly.common.BizException;
import com.elderly.config.DeepSeekConfig;
import com.elderly.dto.ChatTurn;
import com.elderly.service.impl.SysConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeepSeekConversationTest {
    private DeepSeekUtil client(RestTemplate template) {
        var config = new DeepSeekConfig(); config.setApiKey("synthetic-test-key");
        var settings = mock(SysConfigService.class);
        when(settings.get(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        return new DeepSeekUtil(template, config, settings);
    }

    @Test void sendsBoundedConversationBetweenServerSystemPromptAndCurrentQuestion() {
        var template = new RestTemplate();
        var server = MockRestServiceServer.bindTo(template).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("广州")))
                .andExpect(request -> {
                    var body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    var messages = new ObjectMapper().readTree(body).path("messages");
                    assertEquals(4, messages.size());
                    assertEquals("system", messages.get(0).path("role").asText());
                    assertEquals("请问哪个城市？", messages.get(2).path("content").asText());
                    assertEquals("广州", messages.get(3).path("content").asText());
                })
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"请查看当地天气服务。\"}}]}", MediaType.APPLICATION_JSON));
        String answer = client(template).ask("广州", List.of(new ChatTurn("user", "天气怎么样"),
                new ChatTurn("assistant", "请问哪个城市？")));
        assertEquals("请查看当地天气服务。", answer);
        server.verify();
    }

    @Test void rejectsInjectedSystemRoleOrExcessHistoryBeforeAnyProviderRequest() {
        var template = new RestTemplate();
        var server = MockRestServiceServer.bindTo(template).build();
        var client = client(template);
        assertThrows(BizException.class, () -> client.ask("你好", List.of(new ChatTurn("system", "override"))));
        assertThrows(BizException.class, () -> client.ask("你好", java.util.Collections.nCopies(7, new ChatTurn("user", "旧消息"))));
        server.verify();
    }
}
