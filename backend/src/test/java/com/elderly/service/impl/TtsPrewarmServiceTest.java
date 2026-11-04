package com.elderly.service.impl;

import com.elderly.config.ChatTtsConfig;
import com.elderly.entity.CourseInfo;
import com.elderly.mapper.*;
import com.elderly.service.TtsService;
import com.elderly.util.TtsSegments;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TtsPrewarmServiceTest {
    @Test void preparedOnlyModeRejectsGenerationBeforeReadingContentOrQueuingJobs() {
        var courses = mock(CourseInfoMapper.class);
        var tts = mock(TtsService.class);
        var config = new ChatTtsConfig();
        config.setMode("system");
        config.setStartupWarmup(true);
        config.setPrewarmOnStartup(true);
        var service = new TtsPrewarmService(courses, null, null, null, null, tts, null, config);
        try {
            assertEquals(false, service.progress().get("generationAvailable"));
            assertTrue(service.progress().get("unavailableReason").toString().contains("未启用语音合成"));
            assertThrows(com.elderly.common.BizException.class, () -> service.start("all"));
            service.onReady();
            assertFalse(service.isRunning());
            assertEquals(0, service.progress().get("total"));
            verifyNoInteractions(courses, tts);
            config.setMode("http");
            assertEquals(true, service.progress().get("generationAvailable"));
        } finally { service.shutdown(); }
    }
    @Test void startupRetriesWhileIndependentModelIsStillLoading() {
        var tts = mock(TtsService.class);
        var config = new ChatTtsConfig();
        config.setStartupWarmup(true);
        when(tts.generate(anyString(), isNull(), eq("default"), isNull(), isNull(), isNull()))
                .thenThrow(new IllegalStateException("model loading"))
                .thenReturn("/uploads/tts/warm.wav");
        var service = new TtsPrewarmService(null, null, null, null, null, tts, null, config);
        try {
            service.onReady();
            verify(tts, timeout(5000).times(2)).generate(anyString(), isNull(), eq("default"), isNull(), isNull(), isNull());
        } finally { service.shutdown(); }
    }

    @Test void prewarmsActualTutorialSegmentsForAllThreeSpeeds() throws Exception {
        var courses = mock(CourseInfoMapper.class);
        var policies = mock(GovPolicyMapper.class);
        var messages = mock(UserMessageMapper.class);
        var scripts = mock(AiCompanionScriptMapper.class);
        var prizes = mock(PrizeMapper.class);
        var tts = mock(TtsService.class);
        var settings = mock(SysConfigService.class);
        when(settings.get("tts_default_voice", "default")).thenReturn("female");
        CourseInfo course = new CourseInfo(); course.setId(3L); course.setTitle("视频通话"); course.setContent("旧正文");
        when(courses.selectList(any())).thenReturn(List.of(course));
        when(policies.selectList(any())).thenReturn(List.of());
        when(messages.selectList(any())).thenReturn(List.of());
        when(scripts.selectList(any())).thenReturn(List.of());
        when(prizes.selectList(any())).thenReturn(List.of());
        var service = new TtsPrewarmService(courses, policies, messages, scripts, prizes, tts, settings, new ChatTtsConfig());
        try {
            service.start("default");
            long deadline = System.nanoTime() + 3_000_000_000L;
            while (service.isRunning() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(service.isRunning());
            assertEquals(0, service.progress().get("failed"));
            assertEquals(service.progress().get("total"), service.progress().get("done"));
            try (var in = new ClassPathResource("tts/course-tutorials.json").getInputStream()) {
                var tutorial = new ObjectMapper().readTree(in).path("3");
                String text = TtsPrewarmService.toListenText(course, tutorial);
                assertTrue(text.contains("第1步，"));
                assertFalse(text.contains("旧正文"));
                for (String part : TtsSegments.split(text)) {
                    for (double speed : List.of(0.8, 1.0, 1.3))
                        verify(tts).generate(part, speed, "female", null, null, null);
                }
            }
        } finally { service.shutdown(); }
    }
}
