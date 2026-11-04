package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WeatherService {
    private final SysUserMapper users;
    private final WeatherGateway gateway;
    private final PublicWeatherService publicWeather;
    private record Cached(Instant expires, Map<String, Object> data) {}
    private final Map<String, Cached> cache = new LinkedHashMap<>();

    public Map<String, Object> current(Long userId) {
        if (userId == null) throw new BizException("未登录");
        SysUser user = users.selectById(userId);
        if (user == null) throw new BizException("用户不存在");
        if (blank(user.getCity()) || blank(user.getProvince())) return state("NEED_REGION", "请先设置常住地区，获取当地天气提醒");
        String region = user.getProvince() + user.getCity() + Objects.toString(user.getDistrict(), "");
        synchronized (cache) {
            Cached cached = cache.get(region);
            if (cached != null && cached.expires().isAfter(Instant.now())) return cached.data();
        }
        Map<String, Object> result;
        try { result = withFallback(user); }
        catch (Exception e) { result = state("UNAVAILABLE", "天气暂时获取失败，请稍后重试"); }
        synchronized (cache) {
            if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
            cache.put(region, new Cached(Instant.now().plusSeconds("READY".equals(result.get("status")) ? 300 : 60), result));
        }
        return result;
    }

    private Map<String, Object> withFallback(SysUser user) throws Exception {
        Map<String, Object> official = null;
        if (gateway.configured()) {
            try { official = fetch(user); } catch (Exception ignored) { }
            if (official != null && "READY".equals(official.get("status")) && Boolean.TRUE.equals(official.get("weatherAvailable"))) return official;
        }
        try {
            Map<String, Object> free = publicWeather.build(user);
            if (free == null || !"READY".equals(free.get("status"))) throw new IllegalStateException("Weather unavailable");
            if (official != null && Boolean.TRUE.equals(official.get("warningsAvailable"))) {
                free.put("warningsAvailable", true); free.put("alerts", official.get("alerts"));
                free.put("locationId", official.get("locationId"));
                List<String> attribution = new ArrayList<>((List<String>) free.get("attributions"));
                attribution.addAll((List<String>) official.get("attributions"));
                free.put("attributions", attribution);
            }
            return free;
        } catch (Exception error) {
            if (official != null && "READY".equals(official.get("status"))) return official;
            throw error;
        }
    }

    private Map<String, Object> fetch(SysUser user) throws Exception {
        String place = blank(user.getDistrict()) ? user.getCity() : user.getDistrict();
        JsonNode geo = gateway.get("/geo/v2/city/lookup?range=cn&lang=zh&location=" + enc(place) + "&adm=" + enc(user.getCity()));
        if (!"200".equals(geo.path("code").asText())) throw new IllegalStateException();
        JsonNode location = null;
        for (JsonNode candidate : geo.path("location")) {
            if (sameRegion(candidate.path("adm1").asText(), user.getProvince()) &&
                    sameRegion(candidate.path("adm2").asText(), user.getCity())) { location = candidate; break; }
        }
        if (location == null) return state("UNAVAILABLE", "未能准确匹配常住地区，请检查地区设置");
        String lat = location.path("lat").asText(), lon = location.path("lon").asText();
        double latitude = Double.parseDouble(lat), longitude = Double.parseDouble(lon);
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new IllegalStateException();
        String coordinate = latitude + "/" + longitude;
        Map<String, Object> data = new HashMap<>();
        data.put("status", "READY");
        data.put("source", "和风天气");
        data.put("region", user.getProvince() + " " + user.getCity() + " " + location.path("name").asText());
        data.put("locationId", location.path("id").asText());
        data.put("fetchedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        data.put("weatherAvailable", false); data.put("warningsAvailable", false);
        data.put("alerts", List.of());
        List<String> attribution = new ArrayList<>();
        try {
            JsonNode now = gateway.get("/weather/v1/current/" + coordinate + "?lang=zh");
            if (!now.path("temperature").path("value").isNumber() || now.path("condition").path("text").asText().isBlank()) throw new IllegalStateException();
            data.put("temperature", now.path("temperature").path("value").asDouble());
            data.put("condition", now.path("condition").path("text").asText());
            data.put("weatherAvailable", true);
            collectAttribution(now, attribution);
        } catch (Exception ignored) { /* Weather and alerts fail independently. */ }
        try {
            JsonNode warning = gateway.get("/weatheralert/v1/current/" + coordinate + "?lang=zh");
            if (!warning.path("alerts").isArray() && !warning.path("metadata").path("zeroResult").asBoolean()) throw new IllegalStateException();
            data.put("alerts", activeAlerts(warning));
            data.put("warningsAvailable", true);
            collectAttribution(warning, attribution);
        } catch (Exception ignored) { /* Never describe failed lookup as no warnings. */ }
        data.put("attributions", attribution.stream().distinct().toList());
        if (!(boolean) data.get("weatherAvailable") && !(boolean) data.get("warningsAvailable")) return state("UNAVAILABLE", "天气与预警暂时获取失败，请稍后重试");
        return data;
    }

    static List<Map<String, String>> activeAlerts(JsonNode response) {
        Set<String> superseded = new HashSet<>();
        for (JsonNode alert : response.path("alerts")) for (JsonNode id : alert.path("messageType").path("supersedes")) superseded.add(id.asText());
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode a : response.path("alerts")) {
            if (a.path("id").asText().isBlank() || superseded.contains(a.path("id").asText()) || "cancel".equals(a.path("messageType").path("code").asText()) || "past".equals(a.path("urgency").asText())) continue;
            try { if (!OffsetDateTime.parse(a.path("expireTime").asText()).toInstant().isAfter(Instant.now())) continue; }
            catch (Exception e) { continue; }
            Map<String, String> row = new HashMap<>();
            row.put("id", a.path("id").asText());
            row.put("title", a.path("headline").asText("天气预警"));
            row.put("description", a.path("description").asText());
            row.put("instruction", a.path("instruction").asText());
            row.put("sender", a.path("senderName").asText("气象部门"));
            row.put("issuedTime", a.path("issuedTime").asText());
            row.put("expireTime", a.path("expireTime").asText());
            row.put("severity", a.path("severity").asText());
            row.put("color", a.path("color").path("code").asText());
            result.add(row);
        }
        result.sort(Comparator.comparingInt(a -> switch(a.get("severity")) {case "extreme" -> 0; case "severe" -> 1; case "moderate" -> 2; default -> 3;}));
        return result;
    }
    private static void collectAttribution(JsonNode response, List<String> values) {
        for (JsonNode a : response.path("metadata").path("attributions")) values.add(a.asText());
    }
    private static boolean sameRegion(String a, String b) {
        return !blank(a) && !blank(b) && (a.equals(b) || a.replaceAll("省$|市$", "").equals(b.replaceAll("省$|市$", "")));
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static Map<String, Object> state(String status, String message) { return Map.of("status", status, "message", message, "alerts", List.of()); }
}
