package com.elderly.util;

import com.elderly.config.ChatTtsConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestTemplate;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ChatTtsHttpTest {
    @TempDir Path dir;
    HttpServer server;
    ChatTtsConfig config;
    ChatTtsUtil tts;
    AtomicInteger calls = new AtomicInteger();
    List<String> bodies = new CopyOnWriteArrayList<>();
    volatile byte[] audio = "RIFF0000WAVEtest-audio".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/generate", exchange -> {
            // 与 Python 服务一致，要求固定长度请求体。
            if (exchange.getRequestHeaders().getFirst("Content-Length") == null) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            calls.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, audio.length);
            exchange.getResponseBody().write(audio);
            exchange.close();
        });
        server.start();
        config = new ChatTtsConfig();
        config.setMode("http");
        config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/generate");
        tts = new ChatTtsUtil(new RestTemplate(), config, dir.toString());
    }
    @AfterEach void stop() { server.stop(0); }

    String generate(String voice, double speed) { return tts.generate("欢迎学习。", speed, voice, null, null, null); }

    @Test void mergesConcurrentRequestsAndPersistsCacheAcrossInstances() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) results.add(executor.submit(() -> generate("female", 1)));
            String url = results.get(0).get();
            for (var result : results) assertEquals(url, result.get());
            assertEquals(1, calls.get());
            tts = new ChatTtsUtil(new RestTemplate(), config, dir.toString());
            assertEquals(url, generate("female", 1));
            assertEquals(1, calls.get());
            assertTrue(Files.size(dir.resolve("tts").resolve(url.substring(url.lastIndexOf('/') + 1))) > 0);
            assertTrue(bodies.get(0).contains("\"voice\":\"female\""));
            generate("male", 1);
            generate("female", 0.8);
            config.setCacheVersion("v2");
            generate("female", 1);
            assertEquals(4, calls.get());
        } finally { executor.shutdownNow(); }
    }

    @Test void failedAudioIsNotCachedAndFormatMatchesBytes() {
        audio = "not audio".getBytes();
        assertThrows(RuntimeException.class, () -> generate("female", 1));
        audio = "ID3test-mp3".getBytes();
        assertTrue(generate("female", 1).endsWith(".mp3"));
        assertEquals(2, calls.get());
    }
}
