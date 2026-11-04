package com.elderly.service.impl;
import com.elderly.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in live test with a public city, never a real user's location. */
@EnabledIfEnvironmentVariable(named = "WEATHER_LIVE_TEST", matches = "true")
class PublicWeatherLiveTest {
    @Test
    void publicWeatherWorksWithoutAnApiKey() throws Exception {
        SysUser city = new SysUser(); city.setProvince("北京市"); city.setCity("北京市");
        var service = new PublicWeatherService(new PublicWeatherGateway(new ObjectMapper()));
        var weather = service.build(city);
        assertEquals("READY", weather.get("status"));
        assertEquals(true, weather.get("weatherAvailable"));
        assertInstanceOf(Number.class, weather.get("temperature"));
        assertEquals(false, weather.get("warningsAvailable"));
    }
}
