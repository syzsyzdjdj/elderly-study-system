package com.elderly.dto;

import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

/**
 * 重置密码请求
 */
@Data
public class ResetPwdDTO {

    /** 用户ID */
    @NotNull @Positive
    private Long id;

    @NotBlank(message = "请输入新密码")
    @Size(min = 12, max = 72, message = "管理端设置的密码须为12至72字符")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String newPassword;
}
