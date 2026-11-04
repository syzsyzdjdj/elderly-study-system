package com.elderly.service;

import com.elderly.entity.SysRole;

import java.util.List;

/**
 * 系统角色服务
 */
public interface SysRoleService {

    /** 角色列表(admin) */
    List<SysRole> list();

    /** 给角色分配菜单(admin)：先删后插 */
    void assignMenu(Long roleId, List<Long> menuIds);
}
