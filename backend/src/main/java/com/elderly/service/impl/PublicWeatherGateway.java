package com.elderly.service.impl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
@Component
@RequiredArgsConstructor
public class PublicWeatherGateway {
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public JsonNode get(String url) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !java.util.Set.of("geocoding-api.open-meteo.com", "api.open-meteo.com").contains(uri.getHost())) throw new IllegalArgumentException();
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Weather unavailable");
        return json.readTree(response.body());
    }
}
