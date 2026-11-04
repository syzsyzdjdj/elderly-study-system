
package com.elderly.dto;

import com.elderly.entity.SysUser;
import lombok.Data;

/**
 * 登录返回结果
 */
@Data
public class LoginResult {

    /** JWT token */
    private String token;

    /** 用户信息(不含密码) */
    private SysUser userInfo;

    /** 角色标识(admin/course_manager/user) */
    private String roleKey;
}
