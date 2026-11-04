package com.elderly.service.impl;
import com.elderly.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PublicWeatherServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test
    void cityWeatherNeedsNoCredentialsAndIncludesAttribution() throws Exception {
        PublicWeatherGateway gateway = mock(PublicWeatherGateway.class);
        SysUser user = new SysUser(); user.setCity("北京市"); user.setProvince("北京市");
        when(gateway.get(contains("/v1/search"))).thenReturn(json.readTree("{\"results\":[{\"id\":1,\"name\":\"北京\",\"admin1\":\"北京市\",\"country_code\":\"CN\",\"latitude\":39.9,\"longitude\":116.4}]}"));
        when(gateway.get(contains("/v1/forecast"))).thenReturn(json.valueToTree(Map.of("current", Map.of("temperature_2m", 36, "weather_code", 61, "time", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString(), "precipitation", 1))));
        var result = new PublicWeatherService(gateway).build(user);
        assertEquals("READY", result.get("status"));
        assertEquals(false, result.get("warningsAvailable"));
        assertEquals("小雨", result.get("condition"));
        assertEquals(2, ((List<?>) result.get("advisories")).size());
        assertFalse(((List<?>) result.get("attributions")).isEmpty());
    }
    @Test
    void ambiguousCityInWrongProvinceIsRejected() throws Exception {
        PublicWeatherGateway gateway = mock(PublicWeatherGateway.class);
        SysUser user = new SysUser(); user.setCity("北京市"); user.setProvince("北京市");
        when(gateway.get(anyString())).thenReturn(json.readTree("{\"results\":[{\"name\":\"北京\",\"country_code\":\"CN\",\"admin1\":\"四川省\"}]}"));
        assertThrows(IllegalStateException.class, () -> new PublicWeatherService(gateway).build(user));
        verify(gateway, times(1)).get(anyString());
    }
    @Test
    void dailyLifeRemindersHaveStableIdsAndNeverBecomeOfficialAlerts() {
        var now = json.valueToTree(Map.of("temperature_2m", -1, "wind_speed_10m", 45));
        var a = PublicWeatherService.advisories(now, "city", "2026-10-15");
        assertEquals(2, a.size());
        assertEquals(a, PublicWeatherService.advisories(now, "city", "2026-10-15"));
        assertFalse(a.get(0).containsKey("severity"));
        assertEquals("晴", PublicWeatherService.condition(0));
    }
}
