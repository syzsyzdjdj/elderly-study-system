package com.elderly.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.entity.SysOperationLog;
import com.elderly.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统操作日志审计（仅管理员）
 */
@RestController
@RequestMapping("/api/log")
@RequiredArgsConstructor
public class OpLogController {

    private final SysOperationLogMapper operationLogMapper;

    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<SysOperationLog>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String module,
                                              @RequestParam(required = false) String username) {
        QueryWrapper<SysOperationLog> wrapper = new QueryWrapper<SysOperationLog>().orderByDesc("id");
        if (module != null && !module.isEmpty()) {
            wrapper.eq("module", module);
        }
        if (username != null && !username.isEmpty()) {
            wrapper.like("username", username);
        }
        return Result.success(operationLogMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
