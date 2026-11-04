package com.elderly.service.impl;
import com.elderly.entity.SysUser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Key-free, city-level weather estimates for non-commercial educational use. */
@Service
@RequiredArgsConstructor
public class PublicWeatherService {
    private final PublicWeatherGateway gateway;
    public Map<String, Object> build(SysUser user) throws Exception {
        String city = normalize(user.getCity());
        JsonNode geo = gateway.get("https://geocoding-api.open-meteo.com/v1/search?count=20&language=zh&countryCode=CN&name=" + URLEncoder.encode(city, StandardCharsets.UTF_8));
        JsonNode place = null;
        for (JsonNode candidate : geo.path("results")) {
            if ("CN".equals(candidate.path("country_code").asText()) && normalize(candidate.path("admin1").asText()).equals(normalize(user.getProvince())) &&
                (normalize(candidate.path("name").asText()).equals(city) || normalize(candidate.path("admin2").asText()).equals(city))) { place = candidate; break; }
        }
        if (place == null) throw new IllegalStateException("Region not matched");
        double lat = place.path("latitude").asDouble(Double.NaN), lon = place.path("longitude").asDouble(Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || Math.abs(lat)>90 || Math.abs(lon)>180) throw new IllegalStateException();
        JsonNode response = gateway.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,weather_code,precipitation,wind_speed_10m&wind_speed_unit=kmh&temperature_unit=celsius&timezone=Asia%2FShanghai");
        JsonNode now = response.path("current");
        if (!now.path("temperature_2m").isNumber() || !now.path("weather_code").isInt()) throw new IllegalStateException();
        LocalDateTime modelTime = LocalDateTime.parse(now.path("time").asText());
        Instant observed = modelTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        if (observed.isBefore(Instant.now().minusSeconds(7200)) || observed.isAfter(Instant.now().plusSeconds(3600))) throw new IllegalStateException("Stale weather");
        double temp = now.path("temperature_2m").asDouble();
        String location = "open-meteo:" + place.path("id").asText();
        List<Map<String, String>> tips = advisories(now, location, modelTime.toLocalDate().toString());
        Map<String, Object> result = new HashMap<>();
        result.put("status", "READY"); result.put("region", user.getProvince()+" "+user.getCity()+"（市级天气）");
        result.put("locationId", location); result.put("weatherAvailable", true); result.put("warningsAvailable", false);
        result.put("warningsMessage", "当前使用免密钥天气源，未接入官方气象预警；下方为生活提醒。");
        result.put("source", "Open-Meteo（模式估算）"); result.put("temperature", temp);
        result.put("condition", condition(now.path("weather_code").asInt()));
        result.put("weatherTime", modelTime.toString()); result.put("fetchedAt", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString());
        result.put("alerts", List.of()); result.put("advisories", tips);
        result.put("attributions", List.of("Weather data by Open-Meteo.com (CC BY 4.0)", "https://open-meteo.com/", "Location data by GeoNames.org"));
        return result;
    }
    static List<Map<String, String>> advisories(JsonNode now, String location, String date) {
        List<Map<String, String>> tips = new ArrayList<>();
        if (now.path("temperature_2m").asDouble() >= 35) add(tips, location, date, "hot", "高温生活提醒", "当前气温较高，请尽量避开炎热时段外出，留意室内通风。 ");
        if (now.path("temperature_2m").asDouble() <= 0) add(tips, location, date, "cold", "低温生活提醒", "当前气温较低，出门记得添衣保暖，留意路面结冰。 ");
        if (now.path("precipitation").asDouble() >= 0.1) add(tips, location, date, "rain", "雨雪出行提醒", "当前可能有降水，出门带好雨具，注意路面湿滑。 ");
        if (now.path("wind_speed_10m").asDouble() >= 40) add(tips, location, date, "wind", "大风生活提醒", "当前风力较大，请远离广告牌和临时搭建物，留意出行安全。 ");
        return tips;
    }
    private static void add(List<Map<String, String>> tips, String location, String date, String code, String title, String text) {
        tips.add(Map.of("id", location+":"+date+":"+code, "title", title, "description", text.trim()));
    }
    private static String normalize(String s) { return s == null ? "" : s.replaceAll("特别行政区$|壮族自治区$|回族自治区$|维吾尔自治区$|自治区$|省$|市$", ""); }
    static String condition(int code) {
        return switch(code) {
            case 0 -> "晴"; case 1,2 -> "晴间多云"; case 3 -> "阴";
            case 45,48 -> "雾"; case 51,53,55 -> "毛毛雨"; case 56,57,66,67 -> "冻雨";
            case 61 -> "小雨"; case 63 -> "中雨"; case 65 -> "大雨";
            case 71,73,75,77,85,86 -> "降雪"; case 80,81,82 -> "阵雨";
            case 95,96,99 -> "雷雨"; default -> "天气状况待更新";
        };
    }
}
