package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.service.impl.BriefService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 当前登录用户的个人学习周报
 */
@RestController
@RequestMapping("/api/brief")
@RequiredArgsConstructor
public class BriefController {

    private final BriefService briefService;

    /** 本人近七天学习周报，用户身份仅从登录上下文读取 */
    @GetMapping("/latest")
    public Result<Map<String, Object>> latest() {
        return Result.success(briefService.buildBrief(SecurityUtil.getCurrentUserId()));
    }
}
