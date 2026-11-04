package com.elderly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码请设置6至72个字符")
    private String password;

    /** 昵称(可选，默认取用户名) */
    private String nickname;

    /** 手机号(可选) */
    private String phone;

    /** 常住省 */
    private String province;

    /** 常住市 */
    private String city;

    /** 常住区县 */
    private String district;
}
