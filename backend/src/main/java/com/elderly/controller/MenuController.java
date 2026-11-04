package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.SysMenu;
import com.elderly.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理（仅 ADMIN）
 */
@RestController
@RequestMapping("/api/menu")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MenuController {

    private final SysMenuService sysMenuService;

    /** 菜单树 */
    @GetMapping("/list")
    public Result<List<SysMenu>> list() {
        return Result.success(sysMenuService.listTree());
    }
}
