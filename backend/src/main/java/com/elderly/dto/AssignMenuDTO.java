package com.elderly.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色分配菜单请求
 */
@Data
public class AssignMenuDTO {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    /** 菜单ID列表 */
    private List<Long> menuIds;
}
