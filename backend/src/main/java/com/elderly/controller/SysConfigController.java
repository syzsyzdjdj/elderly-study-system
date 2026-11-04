package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.SysConfig;
import com.elderly.service.impl.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数配置（TTS / AI 等后台可配）
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    /** 全部参数（仅管理员；密钥在前端以密码框展示） */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysConfig>> list() {
        return Result.success(configService.list());
    }

    /** 批量保存 */
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> save(@RequestBody List<SysConfig> configs) {
        configService.saveBatch(configs);
        configService.refresh();
        return Result.success();
    }
}
