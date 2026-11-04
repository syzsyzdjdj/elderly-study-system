package com.elderly.controller;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.service.impl.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weather;
    @GetMapping("/current")
    public Result<Map<String, Object>> current() { return Result.success(weather.current(SecurityUtil.getCurrentUserId())); }
}
