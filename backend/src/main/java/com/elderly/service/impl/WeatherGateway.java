package com.elderly.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;

/** Credentials stay on the server. Only the configured QWeather host is contacted. */
@Component
public class WeatherGateway {
    private final String host;
    private final String key;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public WeatherGateway(@Value("${weather.api-host:}") String host,
                          @Value("${weather.api-key:}") String key, ObjectMapper json) {
        this.host = host.trim(); this.key = key.trim(); this.json = json;
    }
    public boolean configured() {
        return host.matches("[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*\\.qweatherapi\\.com") && !key.isBlank();
    }
    public JsonNode get(String path) throws Exception {
        if (!configured()) throw new IllegalStateException("Weather not configured");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + host + path))
                .timeout(Duration.ofSeconds(4)).header("X-QW-Api-Key", key)
                .header("Accept-Encoding", "gzip").GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IllegalStateException("Weather provider unavailable");
        byte[] bytes = response.body();
        if (response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
            try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = zip.readAllBytes(); }
        }
        return json.readTree(bytes);
    }
}
