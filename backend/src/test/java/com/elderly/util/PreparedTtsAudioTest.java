package com.elderly.util;

import com.elderly.config.ChatTtsConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PreparedTtsAudioTest {
    @TempDir Path dir;
    @Test void preparedAudioWorksWithoutModelAndEditedTextNeverUsesOldRecording() throws Exception {
        var config = new ChatTtsConfig(); config.setMode("system");
        var tts = new ChatTtsUtil(new RestTemplate(), config, dir.toString());
        byte[] audio = "RIFF0000WAVErecorded-audio".getBytes();
        String url = PreparedTtsAudio.store(dir.toString(), "欢迎学习。", audio);
        assertEquals(url, tts.generate("**欢迎学习。**", 0.8, "default", null, null, null));
        assertEquals(url, tts.generate("欢迎学习。", 1.3, "default", null, null, null));
        var localError = assertThrows(com.elderly.common.BizException.class,
                () -> tts.generate("欢迎学习。", 1.3, "female", null, null, null));
        assertTrue(localError.getMessage().contains("未启用ChatTTS"));
        var onlineError = assertThrows(com.elderly.common.BizException.class,
                () -> tts.generate("欢迎学习。", 1.0, "edge:xx-female", null, null, null));
        assertTrue(onlineError.getMessage().contains("未启用在线 TTS"));
        assertNull(PreparedTtsAudio.find(dir.toString(), "欢迎学习新课程。"));
        var missing = assertThrows(com.elderly.common.BizException.class,
                () -> tts.generate("欢迎学习新课程。", 1., "default", null, null, null));
        assertTrue(missing.getMessage().contains("这段文字没有对应录音"));
        assertThrows(IllegalArgumentException.class, () -> PreparedTtsAudio.store(dir.toString(), "文字", "not audio".getBytes()));
    }
}
