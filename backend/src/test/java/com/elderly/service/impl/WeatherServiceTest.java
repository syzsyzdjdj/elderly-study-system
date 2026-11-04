package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WeatherServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void unconfiguredOfficialProviderUsesPublicWeather() throws Exception {
        SysUserMapper users = mock(SysUserMapper.class);
        WeatherGateway official = mock(WeatherGateway.class);
        PublicWeatherService free = mock(PublicWeatherService.class);
        SysUser current = user();
        when(users.selectById(5L)).thenReturn(current);
        Map<String, Object> snapshot = new HashMap<>(Map.of("status", "READY", "weatherAvailable", true));
        when(free.build(current)).thenReturn(snapshot);
        assertSame(snapshot, new WeatherService(users, official, free).current(5L));
        verify(official, never()).get(anyString());
    }

    private SysUser user() {
        SysUser u = new SysUser(); u.setProvince("北京市"); u.setCity("北京市"); u.setDistrict("东城区"); return u;
    }
    private JsonNode geo() throws Exception {
        return json.readTree("{\"code\":\"200\",\"location\":[{\"id\":\"101011600\",\"name\":\"东城\",\"adm1\":\"北京\",\"adm2\":\"北京\",\"lat\":\"39.92\",\"lon\":\"116.42\"}]}");
    }
    @Test
    void credentialsAreValidatedWithoutNetwork() {
        assertTrue(new WeatherGateway("abc.xy.qweatherapi.com", "test", json).configured());
        assertFalse(new WeatherGateway("https://abc.qweatherapi.com", "test", json).configured());
        assertFalse(new WeatherGateway("evil.example.com", "test", json).configured());
        assertFalse(new WeatherGateway("abc.qweatherapi.com", "", json).configured());
    }
    @Test
    void missingIdentityIsRejectedBeforeLookup() {
        SysUserMapper users = mock(SysUserMapper.class);
        assertThrows(BizException.class, () -> new WeatherService(users, mock(WeatherGateway.class), mock(PublicWeatherService.class)).current(null));
        verifyNoInteractions(users);
    }
    @Test
    void missingRegionOrCredentialsNeverProducesWeather() {
        SysUserMapper users = mock(SysUserMapper.class);
        WeatherGateway gateway = mock(WeatherGateway.class);
        when(users.selectById(1L)).thenReturn(new SysUser());
        WeatherService service = new WeatherService(users, gateway, mock(PublicWeatherService.class));
        assertEquals("NEED_REGION", service.current(1L).get("status"));
        when(users.selectById(1L)).thenReturn(user());
        assertEquals("UNAVAILABLE", service.current(1L).get("status"));
    }
    @Test
    void cancelledExpiredAndSupersededAlertsAreExcluded() {
        String expiry = OffsetDateTime.now().plusHours(1).toString();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("id", "old", "expireTime", expiry));
        rows.add(Map.of("id", "new", "expireTime", expiry, "messageType", Map.of("code", "update", "supersedes", List.of("old"))));
        rows.add(Map.of("id", "cancelled", "expireTime", expiry, "messageType", Map.of("code", "cancel")));
        rows.add(Map.of("id", "expired", "expireTime", OffsetDateTime.now().minusHours(1).toString()));
        rows.add(Map.of("id", "invalid", "expireTime", "invalid-date"));
        var active = WeatherService.activeAlerts(json.valueToTree(Map.of("alerts", rows)));
        assertEquals(1, active.size());
        assertEquals("new", active.get(0).get("id"));
    }
    @Test
    void alertsStillWorkIfCurrentWeatherFailsAndRegionCacheIsReused() throws Exception {
        SysUserMapper users = mock(SysUserMapper.class);
        WeatherGateway gateway = mock(WeatherGateway.class);
        when(users.selectById(8L)).thenReturn(user());
        when(gateway.configured()).thenReturn(true);
        when(gateway.get(startsWith("/geo/"))).thenReturn(geo());
        when(gateway.get(startsWith("/weather/v1/"))).thenThrow(new IllegalStateException());
        when(gateway.get(startsWith("/weatheralert/"))).thenReturn(json.readTree("{\"metadata\":{\"zeroResult\":true}}"));
        WeatherService service = new WeatherService(users, gateway, mock(PublicWeatherService.class));
        Map<String, Object> result = service.current(8L);
        assertEquals("READY", result.get("status"));
        assertEquals(false, result.get("weatherAvailable"));
        assertEquals(true, result.get("warningsAvailable"));
        assertSame(result, service.current(8L));
        verify(users, times(2)).selectById(8L);
        verify(gateway, times(1)).get(startsWith("/geo/"));
    }
    @Test
    void failedWarningRequestIsNotReportedAsNoWarnings() throws Exception {
        SysUserMapper users = mock(SysUserMapper.class);
        WeatherGateway gateway = mock(WeatherGateway.class);
        when(users.selectById(9L)).thenReturn(user());
        when(gateway.configured()).thenReturn(true);
        when(gateway.get(startsWith("/geo/"))).thenReturn(geo());
        when(gateway.get(startsWith("/weather/v1/"))).thenReturn(json.readTree("{\"temperature\":{\"value\":20},\"condition\":{\"text\":\"晴\"}}"));
        when(gateway.get(startsWith("/weatheralert/"))).thenThrow(new IllegalStateException());
        Map<String, Object> result = new WeatherService(users, gateway, mock(PublicWeatherService.class)).current(9L);
        assertEquals(true, result.get("weatherAvailable"));
        assertEquals(false, result.get("warningsAvailable"));
    }
}
