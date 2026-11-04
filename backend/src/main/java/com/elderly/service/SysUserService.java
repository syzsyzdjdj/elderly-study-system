package com.elderly.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.dto.LoginDTO;
import com.elderly.dto.LoginResult;
import com.elderly.dto.RegisterDTO;
import com.elderly.entity.SysUser;

/**
 * 系统用户服务
 */
public interface SysUserService {

    /** 登录 */
    LoginResult login(LoginDTO loginDTO);

    /** 注册 */
    void register(RegisterDTO registerDTO);

    /** 分页查询用户(admin) */
    Page<SysUser> pageList(long page, long size, String keyword);

    /** 新增用户(admin) */
    void addUser(SysUser user);

    /** 更新用户(admin，password 为空时忽略) */
    void updateUser(SysUser user);

    /** 删除用户(admin) */
    void deleteUser(Long id);

    /** 管理员明确设置新密码，不提供固定默认口令。 */
    void resetPwd(Long id, String newPassword);

    /** 修改自己的密码（校验旧密码） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 更新个人资料（昵称/手机/头像/常住地区/适老化显示参数） */
    SysUser updateProfile(Long userId, SysUser profile);
}
