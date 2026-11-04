package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysUserMapper;
import com.elderly.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人中心（老年端自助）：我的资料、资料更新、修改密码、适老化显示参数。
 * 独立于 SysUserController（该控制器仅 ADMIN 可访问）。
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserService sysUserService;
    private final SysUserMapper sysUserMapper;

    private Long userId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) {
            throw new BizException("请先登录");
        }
        return id;
    }

    /** 查看我的资料 */
    @GetMapping
    public Result<SysUser> me() {
        SysUser user = sysUserMapper.selectById(userId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /** 更新资料：昵称/手机/头像/常住地区/字号/高对比度/音色 */
    @PutMapping
    public Result<SysUser> update(@RequestBody SysUser profile) {
        return Result.success(sysUserService.updateProfile(userId(), profile));
    }

    /** 修改密码（校验旧密码） */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        sysUserService.changePassword(userId(),
                body == null ? null : body.get("oldPassword"),
                body == null ? null : body.get("newPassword"));
        return Result.success();
    }
}
