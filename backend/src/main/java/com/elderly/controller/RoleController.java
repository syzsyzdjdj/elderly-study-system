package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.dto.AssignMenuDTO;
import com.elderly.entity.SysRole;
import com.elderly.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理（仅 ADMIN）
 */
@RestController
@RequestMapping("/api/role")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class RoleController {

    private final SysRoleService sysRoleService;

    /** 角色列表 */
    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.success(sysRoleService.list());
    }

    /** 角色分配菜单（先删旧关联再插入新关联） */
    @PutMapping("/assignMenu")
    public Result<Void> assignMenu(@Valid @RequestBody AssignMenuDTO dto) {
        sysRoleService.assignMenu(dto.getRoleId(), dto.getMenuIds());
        return Result.success();
    }
}
