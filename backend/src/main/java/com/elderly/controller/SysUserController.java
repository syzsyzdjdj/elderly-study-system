package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.dto.ResetPwdDTO;
import com.elderly.entity.SysUser;
import com.elderly.service.SysUserService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理（仅 ADMIN）
 */
@RestController
@RequestMapping("/api/user")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /** 分页查询用户列表 */
    @GetMapping("/list")
    public Result<Page<SysUser>> list(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword) {
        return Result.success(sysUserService.pageList(page, size, keyword));
    }

    /** 新增用户 */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody SysUser user) {
        sysUserService.addUser(user);
        return Result.success();
    }

    /** 更新用户（password 为空则忽略，不更新密码） */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody SysUser user) {
        sysUserService.updateUser(user);
        return Result.success();
    }

    /** 删除用户 */
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.deleteUser(id);
        return Result.success();
    }

    /** 由管理员明确设置新密码。 */
    @PutMapping("/resetPwd")
    public Result<Void> resetPwd(@Valid @RequestBody ResetPwdDTO dto) {
        sysUserService.resetPwd(dto.getId(), dto.getNewPassword());
        return Result.success();
    }
}
